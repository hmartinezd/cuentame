package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.ingredient.*
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class PreparationCostIngredientInput(
    val id: IngredientId, val name: String, val baseUnitSymbol: String,
    val currentCost: CurrentIngredientCost
)
sealed interface CurrentIngredientCost {
    data class Available(val value: BigDecimal) : CurrentIngredientCost
    data object Missing : CurrentIngredientCost
    data object Invalid : CurrentIngredientCost
}
data class PreparationCostComponentInput(
    val id: PreparationRecipeComponentId, val ingredientId: IngredientId,
    val quantityEntered: BigDecimal, val enteredUnitLabel: String?, val quantityBase: BigDecimal,
    val vendorDeltaPerBase: BigDecimal?
)
data class PreparationCostRecipeInput(
    val id: PreparationRecipeId, val outputIngredientId: IngredientId,
    val status: PreparationRecipeStatus, val standardYieldQuantity: BigDecimal?,
    val standardYieldQuantityBase: BigDecimal?, val yieldUnitLabel: String?,
    val components: List<PreparationCostComponentInput>
)

class PreparationCostCalculator @Inject constructor() {
    fun calculate(
        target: PreparationRecipeId,
        recipes: List<PreparationCostRecipeInput>,
        ingredients: Map<IngredientId, PreparationCostIngredientInput>
    ): PreparationRecipeCost? {
        val byId = recipes.associateBy { it.id }
        val activeByOutput = recipes.filter { it.status == PreparationRecipeStatus.ACTIVE }
            .associateBy { it.outputIngredientId }
        return calculateRecipe(byId[target] ?: return null, byId, activeByOutput, ingredients, emptySet())
    }

    private fun calculateRecipe(
        recipe: PreparationCostRecipeInput,
        byId: Map<PreparationRecipeId, PreparationCostRecipeInput>,
        activeByOutput: Map<IngredientId, PreparationCostRecipeInput>,
        ingredients: Map<IngredientId, PreparationCostIngredientInput>,
        visiting: Set<PreparationRecipeId>
    ): PreparationRecipeCost {
        val nextVisiting = visiting + recipe.id
        val components = recipe.components.map { component ->
            val ingredient = ingredients[component.ingredientId]
            val child = activeByOutput[component.ingredientId]
            if (child != null) {
                if (child.id in nextVisiting) {
                    missing(component, ingredient, PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE, child.id)
                } else {
                    val childCost = calculateRecipe(child, byId, activeByOutput, ingredients, nextVisiting)
                    val childYield = child.standardYieldQuantityBase?.takeIf { it > BigDecimal.ZERO }
                    val scaledImpact = childYield?.let { yield ->
                        childCost.priceImpact.knownSubtotal
                            .multiply(component.quantityBase.divide(yield, MathContext.DECIMAL128))
                            .takeIf { childCost.priceImpact.coveredLeafCount > 0 }
                    }
                    val missing = if (childCost.components.any { it.missingReason == PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE }) {
                        PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE
                    } else when (childCost.status) {
                        PreparationCostStatus.FULLY_COSTED -> null
                        PreparationCostStatus.PARTIALLY_COSTED -> PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_PARTIAL
                        PreparationCostStatus.UNCOSTED -> PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_UNCOSTED
                    }
                    if (missing != null || childCost.costPerOutputBaseUnit == null) {
                        missing(component, ingredient, missing ?: PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_YIELD_UNAVAILABLE, child.id,
                            scaledImpact,
                            if (childYield != null) childCost.priceImpact.coveredLeafCount else 0,
                            childCost.priceImpact.totalLeafCount)
                    } else {
                        val unit = childCost.costPerOutputBaseUnit
                        PreparationComponentCost(component.id, component.ingredientId, ingredient?.name.orEmpty(),
                            component.quantityEntered, component.enteredUnitLabel, component.quantityBase,
                            ingredient?.baseUnitSymbol.orEmpty(), unit, component.quantityBase.multiply(unit),
                            PreparationCostSource.ACTIVE_PREPARATION_RECIPE, null,
                            scaledImpact, child.id,
                            childCost.priceImpact.coveredLeafCount, childCost.priceImpact.totalLeafCount)
                    }
                }
            } else {
                when (val cost = ingredient?.currentCost ?: CurrentIngredientCost.Missing) {
                    CurrentIngredientCost.Missing -> missing(component, ingredient, PreparationCostMissingReason.INGREDIENT_COST_MISSING)
                    CurrentIngredientCost.Invalid -> missing(component, ingredient, PreparationCostMissingReason.INGREDIENT_COST_INVALID)
                    is CurrentIngredientCost.Available -> if (cost.value < BigDecimal.ZERO) {
                        missing(component, ingredient, PreparationCostMissingReason.INGREDIENT_COST_INVALID)
                    } else PreparationComponentCost(component.id, component.ingredientId, ingredient!!.name,
                            component.quantityEntered, component.enteredUnitLabel, component.quantityBase,
                            ingredient.baseUnitSymbol, cost.value, component.quantityBase.multiply(cost.value),
                            PreparationCostSource.INGREDIENT_AVERAGE_COST, null,
                            component.vendorDeltaPerBase?.let(component.quantityBase::multiply))
                }
            }
        }
        val costed = components.count { it.componentCurrentCost != null }
        val coverage = when {
            components.isNotEmpty() && costed == components.size -> PreparationCostStatus.FULLY_COSTED
            costed > 0 -> PreparationCostStatus.PARTIALLY_COSTED
            else -> PreparationCostStatus.UNCOSTED
        }
        val known = components.mapNotNull { it.componentCurrentCost }.fold(BigDecimal.ZERO, BigDecimal::add)
        val total = known.takeIf { coverage == PreparationCostStatus.FULLY_COSTED }
        val validYield = recipe.standardYieldQuantity?.takeIf { it > BigDecimal.ZERO }
        val validBaseYield = recipe.standardYieldQuantityBase?.takeIf { it > BigDecimal.ZERO }
        val impactValues = components.mapNotNull { it.vendorPriceImpact }
        val leafCounts = components.map { it.priceImpactCoveredLeafCount to it.priceImpactTotalLeafCount }
        return PreparationRecipeCost(
            recipe.id, coverage, components.size, costed, components.size - costed, known, total,
            recipe.standardYieldQuantity, recipe.yieldUnitLabel,
            if (total != null && validYield != null) total.divide(validYield, MathContext.DECIMAL128) else null,
            if (total != null && validBaseYield != null) total.divide(validBaseYield, MathContext.DECIMAL128) else null,
            ingredients[recipe.outputIngredientId]?.baseUnitSymbol.orEmpty(),
            buildSet {
                if (validYield == null) add(PreparationYieldWarning.MISSING_OR_INVALID_STANDARD_YIELD)
                if (validBaseYield == null) add(PreparationYieldWarning.MISSING_OR_INVALID_BASE_YIELD)
            }, components,
            PreparationPriceImpact(impactValues.fold(BigDecimal.ZERO, BigDecimal::add), leafCounts.sumOf { it.first }, leafCounts.sumOf { it.second })
        )
    }

    private fun missing(c: PreparationCostComponentInput, i: PreparationCostIngredientInput?, reason: PreparationCostMissingReason,
                        nested: PreparationRecipeId? = null, impact: BigDecimal? = null,
                        coveredLeaves: Int = if (impact != null) 1 else 0, totalLeaves: Int = 1) =
        PreparationComponentCost(c.id, c.ingredientId, i?.name.orEmpty(), c.quantityEntered, c.enteredUnitLabel,
            c.quantityBase, i?.baseUnitSymbol.orEmpty(), null, null, null, reason, impact, nested, coveredLeaves, totalLeaves)
}
