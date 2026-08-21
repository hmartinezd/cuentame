package com.venkoi.restaurantops.core.model.inventory

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class InventoryBalance(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val quantityBase: BigDecimal,
    val updatedAt: Instant
)
