package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.IngredientCostProjectionEntity
import com.venkoi.restaurantops.core.model.ingredient.IngredientCost
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
