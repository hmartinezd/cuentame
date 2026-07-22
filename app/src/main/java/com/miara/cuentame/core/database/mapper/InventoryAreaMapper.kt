package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.model.inventory.InventoryArea
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
