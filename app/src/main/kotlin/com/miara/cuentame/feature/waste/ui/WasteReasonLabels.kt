package com.miara.cuentame.feature.waste.ui

import com.miara.cuentame.R
import com.miara.cuentame.core.model.inventory.WasteReason

fun WasteReason.toLabelRes(): Int = when (this) {
    WasteReason.EXPIRED -> R.string.reason_expired
    WasteReason.SPOILED -> R.string.reason_spoiled
    WasteReason.PREPARATION_ERROR -> R.string.reason_preparation_error
    WasteReason.OVERPRODUCTION -> R.string.reason_overproduction
    WasteReason.DROPPED_OR_DAMAGED -> R.string.reason_dropped_or_damaged
    WasteReason.CUSTOMER_RETURN -> R.string.reason_customer_return
    WasteReason.QUALITY_REJECTION -> R.string.reason_quality_rejection
    WasteReason.OTHER -> R.string.reason_other
}
