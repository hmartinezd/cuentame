package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.UnitId
import com.venkoi.restaurantops.core.database.entity.IngredientEntity
import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import java.time.Instant

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = IngredientId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId?.let { IngredientCategoryId(it) },
    baseUnitId = UnitId(baseUnitId),
    defaultAreaId = defaultAreaId?.let { InventoryAreaId(it) },
    sku = sku,
    notes = notes,
    reorderPointBase = reorderPointBase,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
    parLevelBase = parLevelBase
)

fun Ingredient.toEntity(): IngredientEntity = IngredientEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId?.value,
    baseUnitId = baseUnitId.value,
    defaultAreaId = defaultAreaId?.value,
    sku = sku,
    notes = notes,
    reorderPointBase = reorderPointBase,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    parLevelBase = parLevelBase
)
