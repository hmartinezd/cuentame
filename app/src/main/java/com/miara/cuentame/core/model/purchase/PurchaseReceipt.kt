package com.miara.cuentame.core.model.purchase

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
    val createdAt: Instant,
    val updatedAt: Instant,
    val postedAt: Instant? = null,
    val voidedAt: Instant? = null
)
