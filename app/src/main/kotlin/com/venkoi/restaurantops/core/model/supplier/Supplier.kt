package com.venkoi.restaurantops.core.model.supplier

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import java.time.Instant

data class Supplier(
    val id: SupplierId,
    val restaurantId: RestaurantId,
    val name: String,
    val normalizedName: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
