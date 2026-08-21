package com.venkoi.cuentame.core.backup.api

import android.graphics.Bitmap
import java.io.File

/**
 * Abstraction for Android PDF rendering to ensure safe resource management.
 */
interface PurchasePdfRenderer {

    /**
     * Inspects a PDF file to retrieve basic document information.
     */
    suspend fun inspect(file: File): PurchasePdfDocumentInfo

    /**
     * Renders a specific page of a PDF file into a Bitmap.
     * The renderer must handle resource ownership and ensure safe cleanup even if interrupted.
     */
    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        maxDimensionPx: Int
    ): PurchasePdfPageRenderResult
}

data class PurchasePdfDocumentInfo(
    val pageCount: Int,
    val failure: PurchasePdfRenderFailure? = null
)

sealed interface PurchasePdfPageRenderResult {
    data class Success(val bitmap: Bitmap) : PurchasePdfPageRenderResult
    data class Failure(val reason: PurchasePdfRenderFailure) : PurchasePdfPageRenderResult
}

enum class PurchasePdfRenderFailure {
    FileMissing,
    CannotOpen,
    InvalidPage,
    RenderFailed,
    OutOfMemory,
    Unknown
}
