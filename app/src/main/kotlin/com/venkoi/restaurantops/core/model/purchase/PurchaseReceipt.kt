package com.venkoi.restaurantops.core.model.purchase

import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import java.time.Instant

data class PurchaseReceipt(
    val id: PurchaseReceiptId,
    val restaurantId: RestaurantId,
    val supplierId: SupplierId? = null,
    val invoiceNumber: String? = null,
    val purchaseDate: Instant,
    val status: DocumentStatus,
    val notes: String? = null,
    val attachmentPath: String? = null,
    val attachmentDisplayName: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val postedAt: Instant? = null,
    val voidedAt: Instant? = null
)
