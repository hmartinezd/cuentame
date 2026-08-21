package com.venkoi.restaurantops.core.backup.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.venkoi.restaurantops.core.backup.api.PurchasePdfDocumentInfo
import com.venkoi.restaurantops.core.backup.api.PurchasePdfPageRenderResult
import com.venkoi.restaurantops.core.backup.api.PurchasePdfRenderFailure
import com.venkoi.restaurantops.core.backup.api.PurchasePdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPurchasePdfRenderer @Inject constructor() : PurchasePdfRenderer {

    override suspend fun inspect(file: File): PurchasePdfDocumentInfo = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext PurchasePdfDocumentInfo(0, PurchasePdfRenderFailure.FileMissing)
        }

        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    PurchasePdfDocumentInfo(pageCount = renderer.pageCount)
                }
            }
        } catch (e: Exception) {
            PurchasePdfDocumentInfo(0, PurchasePdfRenderFailure.CannotOpen)
        }
    }

    override suspend fun renderPage(
        file: File,
        pageIndex: Int,
        maxDimensionPx: Int
    ): PurchasePdfPageRenderResult = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.FileMissing)
        }

        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                        return@withContext PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.InvalidPage)
                    }

                    renderer.openPage(pageIndex).use { page ->
                        if (maxDimensionPx <= 0) {
                            return@withContext PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.RenderFailed)
                        }

                        // PdfRenderer page dimensions are logical PDF dimensions, not an OCR-ready
                        // raster resolution. Scale the long side to the bounded OCR target, including
                        // upscaling ordinary Letter/A4 pages whose body text would otherwise be tiny.
                        val logicalLongSide = maxOf(page.width, page.height)
                        val scale = maxDimensionPx.toFloat() / logicalLongSide

                        val width = (page.width * scale).toInt().coerceIn(1, maxDimensionPx)
                        val height = (page.height * scale).toInt().coerceIn(1, maxDimensionPx)

                        try {
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            val renderMatrix = Matrix().apply { setScale(scale, scale) }
                            page.render(bitmap, null, renderMatrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.setHasAlpha(false)
                            PurchasePdfPageRenderResult.Success(bitmap)
                        } catch (e: OutOfMemoryError) {
                            PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.OutOfMemory)
                        } catch (e: Exception) {
                            PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.RenderFailed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PurchasePdfPageRenderResult.Failure(PurchasePdfRenderFailure.CannotOpen)
        }
    }
}
