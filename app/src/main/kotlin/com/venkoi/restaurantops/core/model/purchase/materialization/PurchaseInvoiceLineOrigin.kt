package com.venkoi.restaurantops.core.model.purchase.materialization

import com.venkoi.restaurantops.core.common.ids.PurchaseLineId

data class PurchaseInvoiceLineOrigin(
    val purchaseLineId: PurchaseLineId,
    val parseResultId: String,
    val lineIndex: Int,
    val sourceStateFingerprint: String,
    val lastMaterializedSnapshotJson: String
)
