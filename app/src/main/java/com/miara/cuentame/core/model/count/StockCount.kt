package com.miara.cuentame.core.model.count

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.model.inventory.StockCountStatus
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
