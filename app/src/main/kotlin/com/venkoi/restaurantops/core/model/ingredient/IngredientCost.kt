package com.venkoi.restaurantops.core.model.ingredient

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class IngredientCost(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val averageUnitCostBase: BigDecimal,
    val updatedAt: Instant
)
