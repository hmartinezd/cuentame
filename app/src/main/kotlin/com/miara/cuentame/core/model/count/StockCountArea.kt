package com.miara.cuentame.core.model.count

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import java.time.Instant

data class StockCountArea(
    val id: StockCountAreaId,
    val stockCountId: StockCountId,
    val areaId: InventoryAreaId,
    val status: CountAreaStatus,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val sortOrder: Int
)
