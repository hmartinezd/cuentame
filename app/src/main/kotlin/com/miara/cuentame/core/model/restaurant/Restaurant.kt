package com.miara.cuentame.core.model.restaurant

import com.miara.cuentame.core.common.ids.RestaurantId
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
