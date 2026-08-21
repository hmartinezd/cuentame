package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.StockCountAreaId
import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.database.entity.StockCountAreaEntity
import com.venkoi.cuentame.core.model.count.StockCountArea
import com.venkoi.cuentame.core.model.inventory.CountAreaStatus
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
