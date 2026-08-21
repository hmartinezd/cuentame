package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import java.time.Instant

fun InventoryAreaEntity.toDomain(): InventoryArea = InventoryArea(
    id = InventoryAreaId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
)

fun InventoryArea.toEntity(): InventoryAreaEntity = InventoryAreaEntity(
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
