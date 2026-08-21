package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.parsePersistedEnum
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.StockCountAreaId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.database.entity.StockCountAreaEntity
import com.venkoi.restaurantops.core.model.count.StockCountArea
import com.venkoi.restaurantops.core.model.inventory.CountAreaStatus
import java.time.Instant

fun StockCountAreaEntity.toDomain(): StockCountArea = StockCountArea(
    id = StockCountAreaId(id),
    stockCountId = StockCountId(stockCountId),
    areaId = InventoryAreaId(areaId),
    status = parsePersistedEnum(status, CountAreaStatus.UNKNOWN),
    startedAt = startedAt?.let { Instant.ofEpochMilli(it) },
    completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
    sortOrder = sortOrder
)

fun StockCountArea.toEntity(): StockCountAreaEntity = StockCountAreaEntity(
    id = id.value,
    stockCountId = stockCountId.value,
    areaId = areaId.value,
    status = status.name,
    startedAt = startedAt?.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    sortOrder = sortOrder
)
