package com.miara.cuentame.core.backup.api

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/**
 * Abstraction for document scanning to keep ML Kit out of the ViewModel.
 */
interface PurchaseInvoiceScanner {
    /**
     * Gets the IntentSender to start the scanning process.
     */
    suspend fun getStartScanIntent(activity: Activity): IntentSender

    /**
     * Parses the activity result into a domain-specific result.
     */
    fun parseResult(resultCode: Int, data: Intent?): PurchaseInvoiceScanResult
}

sealed interface PurchaseInvoiceScanResult {
    data class Success(
        val pdfUri: Uri,
        val pageCount: Int
    ) : PurchaseInvoiceScanResult

    data object Cancelled : PurchaseInvoiceScanResult

    data class Failure(
        val reason: PurchaseInvoiceScannerFailure
    ) : PurchaseInvoiceScanResult
}

class PurchaseInvoiceScannerException(
    val reason: PurchaseInvoiceScannerFailure,
    cause: Throwable? = null
) : Exception("Scanner failed: ${reason.name}", cause)

enum class PurchaseInvoiceScannerFailure {
    Unavailable,
    UnsupportedDevice,
    ModuleUnavailable,
    LaunchFailed,
    InvalidResult,
    MissingPdf,
    Unknown
}
