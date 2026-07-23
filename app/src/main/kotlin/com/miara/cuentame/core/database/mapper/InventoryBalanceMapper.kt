package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.model.inventory.InventoryBalance
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
