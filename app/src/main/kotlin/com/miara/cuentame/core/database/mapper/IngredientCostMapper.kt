package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.model.ingredient.IngredientCost
import java.math.BigDecimal
import java.time.Instant

fun IngredientCostProjectionEntity.toDomain(): IngredientCost = IngredientCost(
    restaurantId = RestaurantId(restaurantId),
    ingredientId = IngredientId(ingredientId),
    averageUnitCostBase = BigDecimal(averageUnitCostBase),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun IngredientCost.toEntity(): IngredientCostProjectionEntity = IngredientCostProjectionEntity(
    restaurantId = restaurantId.value,
    ingredientId = ingredientId.value,
    averageUnitCostBase = averageUnitCostBase.toPlainString(),
    updatedAt = updatedAt.toEpochMilli()
)
