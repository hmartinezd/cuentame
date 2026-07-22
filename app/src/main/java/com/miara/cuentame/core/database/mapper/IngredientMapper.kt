package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.model.ingredient.Ingredient
import java.math.BigDecimal
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
    reorderPointBase = reorderPointBase?.let { BigDecimal(it) },
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
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
    reorderPointBase = reorderPointBase?.toPlainString(),
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli()
)
