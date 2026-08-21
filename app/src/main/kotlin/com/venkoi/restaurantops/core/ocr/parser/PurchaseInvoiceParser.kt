package com.venkoi.restaurantops.core.ocr.parser

import com.venkoi.restaurantops.core.ocr.api.OcrPageEvidence

/**
 * Pure Kotlin parser that transforms raw OCR evidence into structured invoice data.
 */
interface PurchaseInvoiceParser {

    fun parse(
        pages: List<OcrPageEvidence>
    ): PurchaseInvoiceParseResult
}
