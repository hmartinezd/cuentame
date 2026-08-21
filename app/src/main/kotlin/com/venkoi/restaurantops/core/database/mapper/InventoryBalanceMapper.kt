package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.InventoryBalanceProjectionEntity
import com.venkoi.restaurantops.core.model.inventory.InventoryBalance
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
