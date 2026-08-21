package com.venkoi.restaurantops.core.ocr.api

import android.graphics.Bitmap

data class OcrEngineDescriptor(val id: String) {
    init {
        require(id.isNotBlank()) { "OCR engine ID must not be blank" }
    }
}

/**
 * Interface for OCR engines that can recognize text in purchase invoices.
 */
interface PurchaseInvoiceOcrEngine {

    val descriptor: OcrEngineDescriptor

    /**
     * Recognizes text and layout in the provided [bitmap].
     * Returns the detected evidence.
     */
    suspend fun recognize(
        bitmap: Bitmap
    ): OcrPageEvidence
}
