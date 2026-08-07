package com.miara.cuentame.feature.purchases.presentation

import com.miara.cuentame.R
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerFailure

fun PurchaseInvoiceScannerFailure.toUserMessageRes(): Int = when (this) {
    PurchaseInvoiceScannerFailure.Unavailable -> R.string.error_scanner_unavailable
    PurchaseInvoiceScannerFailure.UnsupportedDevice -> R.string.error_scanner_unavailable
    PurchaseInvoiceScannerFailure.ModuleUnavailable -> R.string.error_scanner_module_downloading
    PurchaseInvoiceScannerFailure.LaunchFailed -> R.string.error_scanner_launch_failed
    PurchaseInvoiceScannerFailure.InvalidResult -> R.string.error_scanner_invalid_result
    PurchaseInvoiceScannerFailure.MissingPdf -> R.string.error_scanner_invalid_result
    PurchaseInvoiceScannerFailure.Unknown -> R.string.error_unknown
}
