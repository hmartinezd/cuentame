package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.OcrPageEvidence

/**
 * Pure Kotlin parser that transforms raw OCR evidence into structured invoice data.
 */
interface PurchaseInvoiceParser {

    fun parse(
        pages: List<OcrPageEvidence>
    ): PurchaseInvoiceParseResult
}
