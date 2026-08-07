package com.miara.cuentame.core.backup.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.miara.cuentame.core.backup.api.PurchasePdfDocumentInfo
import com.miara.cuentame.core.backup.api.PurchasePdfPageRenderResult
import com.miara.cuentame.core.backup.api.PurchasePdfRenderFailure
import com.miara.cuentame.core.backup.api.PurchasePdfRenderer
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
        maxWidthPx: Int
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
                        val scale = if (page.width > maxWidthPx) {
                            maxWidthPx.toFloat() / page.width
                        } else {
                            1.0f
                        }

                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)

                        try {
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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
