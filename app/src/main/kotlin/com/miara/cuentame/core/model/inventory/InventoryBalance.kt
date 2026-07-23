package com.miara.cuentame.core.model.inventory

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class InventoryBalance(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val quantityBase: BigDecimal,
    val updatedAt: Instant
)
