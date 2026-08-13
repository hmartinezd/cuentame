package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.domain.service.InvoiceNumberNormalizer
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceCandidate
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceType
import javax.inject.Inject

class DuplicateInvoiceDetector @Inject constructor(private val purchaseDao: PurchaseDao) {
    suspend fun find(
        restaurantId: String,
        currentReceiptId: String,
        supplierId: String?,
        invoiceNumber: String?,
        sourceSha256: String?
    ): DuplicateInvoiceCandidate? {
        if (!sourceSha256.isNullOrBlank()) {
            purchaseDao.findOtherReceiptByDocumentSha(restaurantId, currentReceiptId, sourceSha256)?.let {
                return DuplicateInvoiceCandidate(
                    DuplicateInvoiceType.SAME_DOCUMENT,
                    PurchaseReceiptId(it.id), PurchaseReceiptId(currentReceiptId),
                    sourceSha256 = sourceSha256
                )
            }
        }
        val normalized = InvoiceNumberNormalizer.normalize(invoiceNumber)
        if (supplierId != null && normalized != null) {
            purchaseDao.findSupplierReceipts(restaurantId, supplierId, currentReceiptId)
                .firstOrNull { InvoiceNumberNormalizer.normalize(it.invoiceNumber) == normalized }
                ?.let {
                    return DuplicateInvoiceCandidate(
                        DuplicateInvoiceType.SAME_SUPPLIER_INVOICE_NUMBER,
                        PurchaseReceiptId(it.id), PurchaseReceiptId(currentReceiptId),
                        SupplierId(supplierId), normalizedInvoiceNumber = normalized
                    )
                }
        }
        return null
    }
}

fun DuplicateInvoiceCandidate.matchesOverride(
    type: String?, existingReceiptId: String?, normalizedInvoiceNumber: String?, sourceSha256: String?
): Boolean = this.type.name == type && this.existingReceiptId.value == existingReceiptId &&
    this.normalizedInvoiceNumber == normalizedInvoiceNumber && this.sourceSha256 == sourceSha256
