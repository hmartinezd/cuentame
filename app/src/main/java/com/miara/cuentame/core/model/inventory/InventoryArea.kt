package com.miara.cuentame.core.model.inventory

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import java.time.Instant

data class InventoryArea(
    val id: InventoryAreaId,
    val restaurantId: RestaurantId,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
