package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
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
