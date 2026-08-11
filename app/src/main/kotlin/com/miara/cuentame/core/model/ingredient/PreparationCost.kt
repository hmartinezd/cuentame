package com.miara.cuentame.core.model.ingredient

import com.miara.cuentame.core.common.ids.*
import java.math.BigDecimal
import java.time.Instant

enum class PreparationCostStatus { FULLY_COSTED, PARTIALLY_COSTED, UNCOSTED }
enum class PreparationCostSource { INGREDIENT_AVERAGE_COST, ACTIVE_PREPARATION_RECIPE }
enum class PreparationCostMissingReason {
    INGREDIENT_COST_MISSING, INGREDIENT_COST_INVALID,
    ACTIVE_NESTED_RECIPE_PARTIAL, ACTIVE_NESTED_RECIPE_UNCOSTED,
    RECIPE_DEPENDENCY_CYCLE
}
enum class PreparationYieldWarning { MISSING_OR_INVALID_STANDARD_YIELD, MISSING_OR_INVALID_BASE_YIELD }

data class PreparationComponentCost(
    val recipeComponentId: PreparationRecipeComponentId,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val quantityEntered: BigDecimal,
    val enteredUnitLabel: String?,
    val quantityBase: BigDecimal,
    val baseUnitSymbol: String,
    val currentUnitCostBase: BigDecimal?,
    val componentCurrentCost: BigDecimal?,
    val costSource: PreparationCostSource?,
    val missingReason: PreparationCostMissingReason?,
    val vendorPriceImpact: BigDecimal?,
    val nestedRecipeId: PreparationRecipeId? = null,
    val priceImpactCoveredLeafCount: Int = if (vendorPriceImpact != null) 1 else 0,
    val priceImpactTotalLeafCount: Int = 1
)

data class PreparationPriceImpact(
    val knownSubtotal: BigDecimal,
    val coveredLeafCount: Int,
    val totalLeafCount: Int
) { val isComplete: Boolean get() = coveredLeafCount == totalLeafCount }

data class HistoricalPreparationCost(
    val productionBatchId: ProductionBatchId,
    val producedAt: Instant,
    val batchCost: BigDecimal?,
    val outputUnitCostBase: BigDecimal?
)

data class PreparationRecipeCost(
    val recipeId: PreparationRecipeId,
    val status: PreparationCostStatus,
    val totalComponentCount: Int,
    val costedComponentCount: Int,
    val missingComponentCount: Int,
    val knownCostSubtotal: BigDecimal,
    val totalBatchCost: BigDecimal?,
    val standardYieldQuantity: BigDecimal?,
    val yieldUnitLabel: String?,
    val costPerYieldUnit: BigDecimal?,
    val costPerOutputBaseUnit: BigDecimal?,
    val outputBaseUnitSymbol: String,
    val yieldWarnings: Set<PreparationYieldWarning>,
    val components: List<PreparationComponentCost>,
    val priceImpact: PreparationPriceImpact,
    val lastProduction: HistoricalPreparationCost? = null,
    val currencyCode: String = "USD"
)

data class PreparationRecipeCostSummary(
    val recipeId: PreparationRecipeId,
    val status: PreparationCostStatus,
    val totalBatchCost: BigDecimal?
)
