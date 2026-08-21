package com.venkoi.restaurantops.core.model.count

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.model.inventory.StockCountStatus
import java.time.Instant

data class StockCount(
    val id: StockCountId,
    val restaurantId: RestaurantId,
    val name: String,
    val startedAt: Instant,
    val effectiveAt: Instant,
    val completedAt: Instant? = null,
    val status: StockCountStatus,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val voidedAt: Instant? = null
)
