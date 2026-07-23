package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import java.time.Instant

fun IngredientCategoryEntity.toDomain(): IngredientCategory = IngredientCategory(
    id = IngredientCategoryId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
)

fun IngredientCategory.toEntity(): IngredientCategoryEntity = IngredientCategoryEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli()
)
