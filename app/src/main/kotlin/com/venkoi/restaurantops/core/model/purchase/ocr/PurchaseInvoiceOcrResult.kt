package com.venkoi.restaurantops.core.model.purchase.ocr

import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.ocr.api.OcrPageEvidence
import java.time.Instant

data class PurchaseInvoiceOcrResult(
    val id: String,
    val purchaseReceiptId: PurchaseReceiptId,
    val sourceDocumentSha256: String,
    val sourceMimeType: String,
    val engine: String,
    val evidenceSchemaVersion: Int,
    val pageCount: Int,
    val fullText: String,
    val processedAt: Instant
)

data class PurchaseInvoiceOcrPage(
    val ocrResultId: String,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val text: String,
    val evidence: OcrPageEvidence
)
