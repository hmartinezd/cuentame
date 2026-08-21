package com.venkoi.cuentame.core.model.restaurant

import com.venkoi.cuentame.core.common.ids.RestaurantId
import java.time.Instant

data class Restaurant(
    val id: RestaurantId,
    val name: String,
    val currencyCode: String,
    val localeTag: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
