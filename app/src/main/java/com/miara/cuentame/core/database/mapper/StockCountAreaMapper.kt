package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.database.entity.StockCountAreaEntity
import com.miara.cuentame.core.model.count.StockCountArea
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import java.time.Instant

fun StockCountAreaEntity.toDomain(): StockCountArea = StockCountArea(
    id = StockCountAreaId(id),
    stockCountId = StockCountId(stockCountId),
    areaId = InventoryAreaId(areaId),
    status = CountAreaStatus.valueOf(status),
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
