package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.venkoi.cuentame.core.model.inventory.InventoryBalance
import java.math.BigDecimal
import java.time.Instant

fun InventoryBalanceProjectionEntity.toDomain(): InventoryBalance = InventoryBalance(
    restaurantId = RestaurantId(restaurantId),
    ingredientId = IngredientId(ingredientId),
    areaId = InventoryAreaId(areaId),
    quantityBase = BigDecimal(quantityBase),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun InventoryBalance.toEntity(): InventoryBalanceProjectionEntity = InventoryBalanceProjectionEntity(
    restaurantId = restaurantId.value,
    ingredientId = ingredientId.value,
    areaId = areaId.value,
    quantityBase = quantityBase.toPlainString(),
    updatedAt = updatedAt.toEpochMilli()
)
