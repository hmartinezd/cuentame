package com.venkoi.cuentame.core.ocr.impl

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.venkoi.cuentame.core.ocr.api.OcrBlockEvidence
import com.venkoi.cuentame.core.ocr.api.OcrElementEvidence
import com.venkoi.cuentame.core.ocr.api.OcrLineEvidence
import com.venkoi.cuentame.core.ocr.api.OcrEngineDescriptor
import com.venkoi.cuentame.core.ocr.api.OcrPageEvidence
import com.venkoi.cuentame.core.ocr.api.OcrPoint
import com.venkoi.cuentame.core.ocr.api.OcrRect
import com.venkoi.cuentame.core.ocr.api.PurchaseInvoiceOcrEngine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MlKitPurchaseInvoiceOcrEngine @Inject constructor() : PurchaseInvoiceOcrEngine {

    override val descriptor = OcrEngineDescriptor("ML_KIT_TEXT_RECOGNITION_V2_LATIN")

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): OcrPageEvidence {
        val ocrBitmap = OcrInputBitmapPreparer.prepare(bitmap)
        return try {
            val image = InputImage.fromBitmap(ocrBitmap, 0)
            val result = recognizer.process(image).await()
            mapToEvidence(ocrBitmap.width, ocrBitmap.height, result)
        } finally {
            if (ocrBitmap !== bitmap) ocrBitmap.recycle()
        }
    }

    private fun mapToEvidence(width: Int, height: Int, text: Text): OcrPageEvidence {
        return OcrPageEvidence(
            widthPx = width,
            heightPx = height,
            text = text.text,
            blocks = text.textBlocks.map { block ->
                OcrBlockEvidence(
                    text = block.text,
                    boundingBox = block.boundingBox?.toOcrRect(),
                    cornerPoints = block.cornerPoints?.map { it.toOcrPoint() } ?: emptyList(),
                    recognizedLanguages = emptyList(),
                    lines = block.lines.map { line ->
                        OcrLineEvidence(
                            text = line.text,
                            boundingBox = line.boundingBox?.toOcrRect(),
                            cornerPoints = line.cornerPoints?.map { it.toOcrPoint() } ?: emptyList(),
                            confidence = line.confidence,
                            angleDegrees = line.angle,
                            recognizedLanguages = emptyList(),
                            elements = line.elements.map { element ->
                                OcrElementEvidence(
                                    text = element.text,
                                    boundingBox = element.boundingBox?.toOcrRect(),
                                    cornerPoints = element.cornerPoints?.map { it.toOcrPoint() } ?: emptyList(),
                                    confidence = element.confidence,
                                    angleDegrees = element.angle,
                                    recognizedLanguages = emptyList()
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private fun Rect.toOcrRect() = OcrRect(left, top, right, bottom)
    private fun Point.toOcrPoint() = OcrPoint(x, y)
}
