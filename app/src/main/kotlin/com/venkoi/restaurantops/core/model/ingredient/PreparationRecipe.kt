package com.venkoi.restaurantops.core.model.ingredient

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.PreparationRecipeComponentId
import com.venkoi.restaurantops.core.common.ids.PreparationRecipeId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class PreparationRecipe(
    val id: PreparationRecipeId,
    val restaurantId: RestaurantId,
    val outputIngredientId: IngredientId,
    val name: String,
    val standardYieldQuantity: BigDecimal?,
    val standardYieldQuantityBase: BigDecimal?,
    val yieldUnitOptionId: IngredientUnitOptionId?,
    val status: PreparationRecipeStatus,
    val notes: String?,
    val components: List<PreparationRecipeComponent>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?
)

data class PreparationRecipeComponent(
    val id: PreparationRecipeComponentId,
    val recipeId: PreparationRecipeId,
    val componentIngredientId: IngredientId,
    val unitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val sortOrder: Int,
    val notes: String?
)

data class PreparationRecipeSummary(
    val id: PreparationRecipeId,
    val outputIngredientId: IngredientId,
    val outputIngredientName: String,
    val recipeName: String,
    val status: PreparationRecipeStatus,
    val standardYieldQuantity: BigDecimal?,
    val yieldUnitLabel: String?,
    val componentCount: Int,
    val updatedAt: Instant
)
