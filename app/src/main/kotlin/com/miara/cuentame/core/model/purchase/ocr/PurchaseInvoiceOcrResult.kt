package com.miara.cuentame.core.model.purchase.ocr

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.ocr.api.OcrPageEvidence
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
