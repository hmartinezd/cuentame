package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.parsePersistedEnum
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.database.entity.StockCountEntity
import com.venkoi.restaurantops.core.model.count.StockCount
import com.venkoi.restaurantops.core.model.inventory.StockCountStatus
import java.time.Instant

fun StockCountEntity.toDomain(): StockCount = StockCount(
    id = StockCountId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    startedAt = Instant.ofEpochMilli(startedAt),
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
    status = parsePersistedEnum(status, StockCountStatus.UNKNOWN),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    voidedAt = voidedAt?.let { Instant.ofEpochMilli(it) }
)

fun StockCount.toEntity(): StockCountEntity = StockCountEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    name = name,
    startedAt = startedAt.toEpochMilli(),
    effectiveAt = effectiveAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    status = status.name,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    voidedAt = voidedAt?.toEpochMilli()
)
