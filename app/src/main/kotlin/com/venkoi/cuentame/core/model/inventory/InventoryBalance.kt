package com.venkoi.cuentame.core.model.inventory

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class InventoryBalance(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val quantityBase: BigDecimal,
    val updatedAt: Instant
)
