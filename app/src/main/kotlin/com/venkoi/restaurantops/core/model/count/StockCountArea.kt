package com.venkoi.restaurantops.core.model.count

import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.StockCountAreaId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.model.inventory.CountAreaStatus
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
