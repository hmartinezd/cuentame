package com.miara.cuentame.core.backup.fakes

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner
import io.mockk.mockk
import kotlinx.coroutines.delay

class FakePurchaseInvoiceScanner : PurchaseInvoiceScanner {
    
    var nextIntentSender: IntentSender? = null
    var startScanError: Exception? = null
    var nextResult: PurchaseInvoiceScanResult = PurchaseInvoiceScanResult.Cancelled
    var preparationDelayMillis: Long = 0
    var parseResultCalls: Int = 0
        private set

    override suspend fun getStartScanIntent(activity: Activity): IntentSender {
        if (preparationDelayMillis > 0) delay(preparationDelayMillis)
        startScanError?.let { throw it }
        return nextIntentSender ?: mockk(relaxed = true)
    }

    override fun parseResult(resultCode: Int, data: Intent?): PurchaseInvoiceScanResult {
        parseResultCalls += 1
        return nextResult
    }
}
