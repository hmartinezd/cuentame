package com.miara.cuentame.core.backup.platform

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerFailure
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GmsPurchaseInvoiceScanner @Inject constructor() : PurchaseInvoiceScanner {

    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(PURCHASE_INVOICE_MAX_PAGES)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    override suspend fun getStartScanIntent(activity: Activity): IntentSender {
        val client = GmsDocumentScanning.getClient(options)
        try {
            return client.getStartScanIntent(activity).await()
        } catch (e: Exception) {
            val failure = when {
                e is MlKitException && e.errorCode == MlKitException.UNSUPPORTED -> 
                    PurchaseInvoiceScannerFailure.UnsupportedDevice
                else -> PurchaseInvoiceScannerFailure.LaunchFailed
            }
            throw PurchaseInvoiceScannerException(failure, e)
        }
    }

    override fun parseResult(resultCode: Int, data: Intent?): PurchaseInvoiceScanResult {
        if (resultCode == Activity.RESULT_CANCELED) {
            return PurchaseInvoiceScanResult.Cancelled
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            return PurchaseInvoiceScanResult.Failure(PurchaseInvoiceScannerFailure.LaunchFailed)
        }

        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
        if (result == null) {
            return PurchaseInvoiceScanResult.Failure(PurchaseInvoiceScannerFailure.InvalidResult)
        }

        val pdf = result.pdf
        if (pdf == null) {
            return PurchaseInvoiceScanResult.Failure(PurchaseInvoiceScannerFailure.MissingPdf)
        }

        return PurchaseInvoiceScanResult.Success(
            pdfUri = pdf.uri,
            pageCount = pdf.pageCount
        )
    }

    companion object {
        private const val PURCHASE_INVOICE_MAX_PAGES = 20
    }
}
