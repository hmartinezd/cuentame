package com.venkoi.cuentame.core.model.purchase

import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.common.ids.SupplierId

enum class DuplicateInvoiceType { SAME_DOCUMENT, SAME_SUPPLIER_INVOICE_NUMBER }

data class DuplicateInvoiceCandidate(
    val type: DuplicateInvoiceType,
    val existingReceiptId: PurchaseReceiptId,
    val currentReceiptId: PurchaseReceiptId,
    val supplierId: SupplierId? = null,
    val normalizedInvoiceNumber: String? = null,
    val sourceSha256: String? = null
)
