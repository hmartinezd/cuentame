package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.UnitId
import com.venkoi.restaurantops.core.database.entity.IngredientUnitOptionEntity
import com.venkoi.restaurantops.core.model.ingredient.IngredientUnitOption
import java.time.Instant

fun IngredientUnitOptionEntity.toDomain(): IngredientUnitOption = IngredientUnitOption(
    id = IngredientUnitOptionId(id),
    ingredientId = IngredientId(ingredientId),
    displayName = displayName,
    shortLabel = shortLabel,
    standardUnitId = standardUnitId?.let { UnitId(it) },
    factorToBase = factorToBase,
    isBase = isBase,
    isDefaultCount = isDefaultCount,
    isDefaultPurchase = isDefaultPurchase,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
)

fun IngredientUnitOption.toEntity(): IngredientUnitOptionEntity = IngredientUnitOptionEntity(
    id = id.value,
    ingredientId = ingredientId.value,
    displayName = displayName,
    shortLabel = shortLabel,
    standardUnitId = standardUnitId?.value,
    factorToBase = factorToBase,
    isBase = isBase,
    isDefaultCount = isDefaultCount,
    isDefaultPurchase = isDefaultPurchase,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli()
)
