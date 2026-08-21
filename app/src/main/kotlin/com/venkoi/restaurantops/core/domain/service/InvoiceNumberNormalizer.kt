package com.venkoi.restaurantops.core.domain.service

import java.util.Locale

object InvoiceNumberNormalizer {
    fun normalize(value: String?): String? = value
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.replace("\\s+".toRegex(), "")
        ?.ifBlank { null }
}
