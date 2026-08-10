package com.miara.cuentame.core.model.purchase.materialization

import com.miara.cuentame.core.common.ids.PurchaseLineId

data class PurchaseInvoiceLineOrigin(
    val purchaseLineId: PurchaseLineId,
    val parseResultId: String,
    val lineIndex: Int,
    val sourceStateFingerprint: String,
    val lastMaterializedSnapshotJson: String
)
