package com.miara.cuentame.core.ocr.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.PurchasePdfPageRenderResult
import com.miara.cuentame.core.backup.platform.AndroidPurchasePdfRenderer
import com.miara.cuentame.core.common.image.SafeImageDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class OcrInputBitmapIntegrationTest {

    @Test
    fun transparentRaster_isOpaqueWhiteAtMlKitPreparationBoundary_andKeepsForeground() {
        val source = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(80, 90, Color.BLACK)
        }

        val prepared = OcrInputBitmapPreparer.prepare(source)
        try {
            assertThat(source.width).isEqualTo(320)
            assertThat(source.height).isEqualTo(180)
            assertThat(Color.alpha(source.getPixel(0, 0))).isEqualTo(0)
            assertThat(prepared.width).isEqualTo(320)
            assertThat(prepared.height).isEqualTo(180)
            assertThat(prepared.config).isEqualTo(Bitmap.Config.ARGB_8888)
            assertThat(prepared.hasAlpha()).isFalse()
            assertThat(prepared.getPixel(0, 0)).isEqualTo(Color.WHITE)
            assertThat(Color.alpha(prepared.getPixel(0, 0))).isEqualTo(255)
            assertThat(prepared.getPixel(80, 90)).isEqualTo(Color.BLACK)
        } finally {
            prepared.recycle()
            source.recycle()
        }
    }

    @Test
    fun importedTransparentPng_decodeAndFinalPreparation_preserveDimensionsAndVisibleInk() = runBlocking {
        val source = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(120f, 100f, 520f, 130f, Paint().apply { color = Color.BLACK })
        }
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()

        val decoded = SafeImageDecoder.decode(
            streamProvider = { ByteArrayInputStream(bytes) },
            maxDimension = 2048
        )!!
        val prepared = OcrInputBitmapPreparer.prepare(decoded)
        try {
            assertThat(decoded.width).isEqualTo(640)
            assertThat(decoded.height).isEqualTo(360)
            assertThat(prepared).isSameInstanceAs(decoded)
            assertThat(prepared.hasAlpha()).isFalse()
            assertThat(prepared.getPixel(0, 0)).isEqualTo(Color.WHITE)
            assertThat(Color.alpha(prepared.getPixel(0, 0))).isEqualTo(255)
            assertThat(prepared.getPixel(200, 110)).isEqualTo(Color.BLACK)
        } finally {
            decoded.recycle()
        }
    }

    @Test
    fun pdfRenderer_producesTheOpaqueWhiteBitmapUsedByOcrPreparation() = runBlocking {
        val file = File.createTempFile("ocr-paper", ".pdf")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
        page.canvas.drawText("CUENTAME INVOICE", 70f, 130f, Paint().apply {
            color = Color.BLACK
            textSize = 52f
        })
        document.finishPage(page)
        file.outputStream().use(document::writeTo)
        document.close()

        val result = AndroidPurchasePdfRenderer().renderPage(file, 0, 2048)
        assertThat(result).isInstanceOf(PurchasePdfPageRenderResult.Success::class.java)
        val rendered = (result as PurchasePdfPageRenderResult.Success).bitmap
        val prepared = OcrInputBitmapPreparer.prepare(rendered)
        try {
            assertThat(prepared).isSameInstanceAs(rendered)
            assertThat(prepared.width).isEqualTo(1536)
            assertThat(prepared.height).isEqualTo(2048)
            assertThat(prepared.hasAlpha()).isFalse()
            assertThat(prepared.getPixel(10, 10)).isEqualTo(Color.WHITE)
            assertThat(hasDarkPixel(prepared, 0, 0, prepared.width, prepared.height)).isTrue()
        } finally {
            rendered.recycle()
            file.delete()
        }
    }

    @Test
    fun realisticLetterPdf_isUpscaledAndRecognizedByRealMlKit() = runBlocking {
        val file = createInvoicePdf(
            listOf(
                listOf(
                    "CHICAGO ITALIAN BREAD     13.29",
                    "FRENCH BREAD               2.49",
                    "TAX 7.5%",
                    "TOTAL                     15.78"
                )
            )
        )

        val result = AndroidPurchasePdfRenderer().renderPage(file, 0, 2048)
        assertThat(result).isInstanceOf(PurchasePdfPageRenderResult.Success::class.java)
        val rendered = (result as PurchasePdfPageRenderResult.Success).bitmap
        try {
            assertThat(rendered.width).isGreaterThan(612)
            assertThat(rendered.height).isGreaterThan(792)
            assertThat(maxOf(rendered.width, rendered.height)).isEqualTo(2048)
            assertThat(rendered.width.toFloat() / rendered.height)
                .isWithin(0.002f).of(612f / 792f)
            assertThat(rendered.config).isEqualTo(Bitmap.Config.ARGB_8888)
            assertThat(rendered.hasAlpha()).isFalse()
            assertThat(rendered.getPixel(10, 10)).isEqualTo(Color.WHITE)
            assertThat(hasDarkPixel(rendered, 0, 0, rendered.width, rendered.height)).isTrue()

            val prepared = OcrInputBitmapPreparer.prepare(rendered)
            assertThat(prepared).isSameInstanceAs(rendered)
            val evidence = MlKitPurchaseInvoiceOcrEngine().recognize(rendered)
            Log.i("OcrInputBitmapTest", "Realistic PDF bitmap=${rendered.width}x${rendered.height}, rawText=${evidence.text}")
            assertThat(evidence.widthPx).isEqualTo(rendered.width)
            assertThat(evidence.heightPx).isEqualTo(rendered.height)
            assertThat(evidence.text).isNotEmpty()
            assertThat(listOf("CHICAGO", "BREAD", "TOTAL").any { evidence.text.uppercase().contains(it) }).isTrue()
        } finally {
            rendered.recycle()
            file.delete()
        }
    }

    @Test
    fun realisticTwoPagePdf_rendersAndRecognizesBothPagesInOrder() = runBlocking {
        val file = createInvoicePdf(
            listOf(
                listOf(
                    "CHICAGO ITALIAN BREAD      13.29",
                    "FRENCH BREAD                2.49",
                    "PAGE TOTAL                 15.78"
                ),
                listOf(
                    "ANOTHER ITEM                 5.00",
                    "TOTAL                       20.78"
                )
            )
        )
        val renderer = AndroidPurchasePdfRenderer()
        val info = renderer.inspect(file)
        assertThat(info.pageCount).isEqualTo(2)
        val pageTexts = mutableListOf<String>()

        try {
            for (pageIndex in 0 until info.pageCount) {
                val result = renderer.renderPage(file, pageIndex, 2048)
                assertThat(result).isInstanceOf(PurchasePdfPageRenderResult.Success::class.java)
                val bitmap = (result as PurchasePdfPageRenderResult.Success).bitmap
                try {
                    val evidence = MlKitPurchaseInvoiceOcrEngine().recognize(bitmap)
                    Log.i("OcrInputBitmapTest", "PDF page=$pageIndex bitmap=${bitmap.width}x${bitmap.height}, rawText=${evidence.text}")
                    assertThat(evidence.text).isNotEmpty()
                    pageTexts += evidence.text.uppercase()
                } finally {
                    bitmap.recycle()
                }
            }

            assertThat(pageTexts).hasSize(2)
            assertThat(pageTexts[0]).contains("BREAD")
            assertThat(pageTexts[1]).contains("TOTAL")
        } finally {
            file.delete()
        }
    }

    @Test
    fun realMlKit_recognizesKnownTextFromTransparentRaster() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawText("CUENTAME INVOICE", 80f, 260f, Paint().apply {
                color = Color.BLACK
                textSize = 120f
                isAntiAlias = true
            })
        }

        try {
            val result = MlKitPurchaseInvoiceOcrEngine().recognize(bitmap)
            Log.i("OcrInputBitmapTest", "ML Kit raw text: ${result.text}")
            assertThat(result.text).isNotEmpty()
            assertThat(result.text.uppercase()).contains("INVOICE")
        } finally {
            bitmap.recycle()
        }
    }

    private fun hasDarkPixel(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (y in top until bottom) for (x in left until right) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) < 80 && Color.green(pixel) < 80 && Color.blue(pixel) < 80) return true
        }
        return false
    }

    private fun createInvoicePdf(pages: List<List<String>>): File {
        val file = File.createTempFile("realistic-invoice", ".pdf")
        val document = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        pages.forEachIndexed { index, lines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, index + 1).create())
            lines.forEachIndexed { lineIndex, line ->
                page.canvas.drawText(line, 54f, 90f + lineIndex * 24f, paint)
            }
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }
}
