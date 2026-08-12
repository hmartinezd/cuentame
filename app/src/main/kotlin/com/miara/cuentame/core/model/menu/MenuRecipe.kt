package com.miara.cuentame.core.model.menu

import com.miara.cuentame.core.common.ids.*
import java.math.BigDecimal
import java.time.Instant

data class MenuRecipe(val id: MenuRecipeId, val restaurantId: RestaurantId, val name: String, val normalizedName: String,
    val sellingPrice: BigDecimal?, val notes: String?, val archivedAt: Instant?, val createdAt: Instant, val updatedAt: Instant)
data class MenuRecipeComponent(val id: MenuRecipeComponentId, val menuRecipeId: MenuRecipeId, val ingredientId: IngredientId,
    val ingredientUnitOptionId: IngredientUnitOptionId, val quantityEntered: BigDecimal, val quantityBase: BigDecimal,
    val sortOrder: Int, val createdAt: Instant, val updatedAt: Instant)

enum class MenuRecipeCostStatus { FULLY_COSTED, PARTIALLY_COSTED, UNCOSTED }
enum class MenuCostSource { INGREDIENT_AVERAGE_COST, ACTIVE_PREPARATION_RECIPE }
enum class MenuRecipeCostMissingReason { INGREDIENT_COST_MISSING, INGREDIENT_COST_INVALID, ACTIVE_PREPARATION_PARTIAL,
    ACTIVE_PREPARATION_UNCOSTED, ACTIVE_PREPARATION_YIELD_UNAVAILABLE, PREPARATION_DEPENDENCY_CYCLE }

data class MenuRecipeComponentCost(val componentId: MenuRecipeComponentId, val ingredientId: IngredientId, val ingredientName: String,
    val quantityEntered: BigDecimal, val enteredUnitLabel: String?, val quantityBase: BigDecimal, val baseUnitSymbol: String,
    val currentUnitCostBase: BigDecimal?, val currentCost: BigDecimal?, val source: MenuCostSource?,
    val missingReason: MenuRecipeCostMissingReason?, val vendorPriceImpact: BigDecimal?, val impactCoveredLeafCount: Int, val impactTotalLeafCount: Int)
data class MenuPriceImpact(val knownSubtotal: BigDecimal, val coveredLeafCount: Int, val totalLeafCount: Int) {
    val isComplete get() = coveredLeafCount == totalLeafCount
}
data class MenuSellingMetrics(val foodCostPercent: BigDecimal?, val grossProfitBeforeLaborAndOverhead: BigDecimal?)
data class MenuRecipeCost(val menuRecipeId: MenuRecipeId, val status: MenuRecipeCostStatus, val componentCount: Int,
    val costedComponentCount: Int, val missingComponentCount: Int, val knownCostSubtotal: BigDecimal,
    val currentPlateCost: BigDecimal?, val sellingPrice: BigDecimal?, val sellingMetrics: MenuSellingMetrics,
    val components: List<MenuRecipeComponentCost>, val missingReasons: Set<MenuRecipeCostMissingReason>,
    val priceImpact: MenuPriceImpact, val currencyCode: String)
