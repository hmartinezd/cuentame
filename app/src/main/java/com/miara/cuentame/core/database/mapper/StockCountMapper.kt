package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.inventory.StockCountStatus
import java.time.Instant

fun StockCountEntity.toDomain(): StockCount = StockCount(
    id = StockCountId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    startedAt = Instant.ofEpochMilli(startedAt),
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
    status = StockCountStatus.valueOf(status),
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
