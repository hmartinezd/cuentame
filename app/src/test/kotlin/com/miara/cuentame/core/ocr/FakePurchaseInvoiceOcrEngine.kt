package com.miara.cuentame.core.ocr

import android.graphics.Bitmap
import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrEngine
import kotlinx.coroutines.delay

class FakePurchaseInvoiceOcrEngine : PurchaseInvoiceOcrEngine {

    var recognizedCalls = 0
    var delayMs: Long = 0
    var nextResult: OcrPageEvidence? = null
    var nextFailure: Throwable? = null

    override suspend fun recognize(bitmap: Bitmap): OcrPageEvidence {
        recognizedCalls++
        if (delayMs > 0) delay(delayMs)
        
        nextFailure?.let { throw it }
        
        return nextResult ?: OcrPageEvidence(
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            text = "Fake OCR Text",
            blocks = emptyList()
        )
    }
}
