package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.menu.*
import java.math.*
import javax.inject.Inject

data class MenuCostIngredientInput(val id: IngredientId, val name: String, val baseUnitSymbol: String, val currentCost: CurrentIngredientCost)
data class MenuCostComponentInput(val id: MenuRecipeComponentId, val ingredientId: IngredientId, val quantityEntered: BigDecimal,
    val enteredUnitLabel: String?, val quantityBase: BigDecimal, val vendorDeltaPerBase: BigDecimal?)

class MenuCostCalculator @Inject constructor() {
    fun calculate(recipeId: MenuRecipeId, sellingPrice: BigDecimal?, components: List<MenuCostComponentInput>,
        ingredients: Map<IngredientId, MenuCostIngredientInput>, activePreparations: Map<IngredientId, PreparationRecipeCost>, currencyCode: String): MenuRecipeCost {
        val costs = components.map { c ->
            val ingredient = ingredients[c.ingredientId]
            val prep = activePreparations[c.ingredientId]
            if (prep != null) prepared(c, ingredient, prep) else raw(c, ingredient)
        }
        val costed = costs.count { it.currentCost != null }
        val status = when { costs.isNotEmpty() && costed == costs.size -> MenuRecipeCostStatus.FULLY_COSTED
            costed > 0 -> MenuRecipeCostStatus.PARTIALLY_COSTED else -> MenuRecipeCostStatus.UNCOSTED }
        val known = costs.mapNotNull { it.currentCost }.fold(BigDecimal.ZERO, BigDecimal::add)
        val plate = known.takeIf { status == MenuRecipeCostStatus.FULLY_COSTED }
        val validPrice = sellingPrice?.takeIf { it > BigDecimal.ZERO }
        val metrics = MenuSellingMetrics(
            if (plate != null && validPrice != null) plate.divide(validPrice, MathContext.DECIMAL128).multiply(BigDecimal("100")) else null,
            if (plate != null && validPrice != null) validPrice.subtract(plate) else null)
        return MenuRecipeCost(recipeId, status, costs.size, costed, costs.size-costed, known, plate, sellingPrice, metrics, costs,
            costs.mapNotNull { it.missingReason }.toSet(), MenuPriceImpact(costs.mapNotNull { it.vendorPriceImpact }.fold(BigDecimal.ZERO, BigDecimal::add),
                costs.sumOf { it.impactCoveredLeafCount }, costs.sumOf { it.impactTotalLeafCount }), currencyCode)
    }

    private fun raw(c: MenuCostComponentInput, i: MenuCostIngredientInput?): MenuRecipeComponentCost {
        val impact = c.vendorDeltaPerBase?.multiply(c.quantityBase)
        val current = when (val value = i?.currentCost ?: CurrentIngredientCost.Missing) {
            is CurrentIngredientCost.Available -> value.value.takeIf { it >= BigDecimal.ZERO }
            else -> null
        }
        val reason = when (val value = i?.currentCost ?: CurrentIngredientCost.Missing) {
            CurrentIngredientCost.Missing -> MenuRecipeCostMissingReason.INGREDIENT_COST_MISSING
            CurrentIngredientCost.Invalid -> MenuRecipeCostMissingReason.INGREDIENT_COST_INVALID
            is CurrentIngredientCost.Available -> if (value.value < BigDecimal.ZERO) MenuRecipeCostMissingReason.INGREDIENT_COST_INVALID else null
        }
        return MenuRecipeComponentCost(c.id,c.ingredientId,i?.name.orEmpty(),c.quantityEntered,c.enteredUnitLabel,c.quantityBase,i?.baseUnitSymbol.orEmpty(),current,
            current?.multiply(c.quantityBase),MenuCostSource.INGREDIENT_AVERAGE_COST.takeIf { current != null },reason,impact,if(impact!=null)1 else 0,1)
    }

    private fun prepared(c: MenuCostComponentInput, i: MenuCostIngredientInput?, p: PreparationRecipeCost): MenuRecipeComponentCost {
        val yield = p.standardYieldQuantityBase?.takeIf { it > BigDecimal.ZERO }
        val impact = yield?.let { p.priceImpact.knownSubtotal.multiply(c.quantityBase.divide(it, MathContext.DECIMAL128)) }
        val reason = when {
            p.components.any { it.missingReason == PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE } -> MenuRecipeCostMissingReason.PREPARATION_DEPENDENCY_CYCLE
            p.status == PreparationCostStatus.PARTIALLY_COSTED -> MenuRecipeCostMissingReason.ACTIVE_PREPARATION_PARTIAL
            p.status == PreparationCostStatus.UNCOSTED -> MenuRecipeCostMissingReason.ACTIVE_PREPARATION_UNCOSTED
            p.costPerOutputBaseUnit == null || yield == null -> MenuRecipeCostMissingReason.ACTIVE_PREPARATION_YIELD_UNAVAILABLE
            else -> null
        }
        val unit = p.costPerOutputBaseUnit.takeIf { reason == null }
        return MenuRecipeComponentCost(c.id,c.ingredientId,i?.name.orEmpty(),c.quantityEntered,c.enteredUnitLabel,c.quantityBase,i?.baseUnitSymbol.orEmpty(),unit,
            unit?.multiply(c.quantityBase),MenuCostSource.ACTIVE_PREPARATION_RECIPE,reason,impact,
            if(impact!=null)p.priceImpact.coveredLeafCount else 0,p.priceImpact.totalLeafCount)
    }
}
