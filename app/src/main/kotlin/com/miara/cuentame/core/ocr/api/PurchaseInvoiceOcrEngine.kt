package com.miara.cuentame.core.ocr.api

import android.graphics.Bitmap

/**
 * Interface for OCR engines that can recognize text in purchase invoices.
 */
interface PurchaseInvoiceOcrEngine {

    /**
     * Recognizes text and layout in the provided [bitmap].
     * Returns the detected evidence.
     */
    suspend fun recognize(
        bitmap: Bitmap
    ): OcrPageEvidence
}
