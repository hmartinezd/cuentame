package com.miara.cuentame.core.model.purchase.materialization.failure

sealed interface PurchaseInvoiceMaterializationFailure {
    object PurchaseNotFound : PurchaseInvoiceMaterializationFailure
    object PurchaseAlreadyPosted : PurchaseInvoiceMaterializationFailure
    object DocumentMissing : PurchaseInvoiceMaterializationFailure
    object DocumentChanged : PurchaseInvoiceMaterializationFailure
    object ParseChanged : PurchaseInvoiceMaterializationFailure
    object InvoiceStateChanged : PurchaseInvoiceMaterializationFailure
    object SupplierChanged : PurchaseInvoiceMaterializationFailure
    object UnresolvedLines : PurchaseInvoiceMaterializationFailure
    object InvalidConfirmedMatch : PurchaseInvoiceMaterializationFailure
    object MissingQuantity : PurchaseInvoiceMaterializationFailure
    object MissingRequiredUnitOption : PurchaseInvoiceMaterializationFailure
    object MissingRequiredArea : PurchaseInvoiceMaterializationFailure
    object DraftChanged : PurchaseInvoiceMaterializationFailure
    object ManualEditConflict : PurchaseInvoiceMaterializationFailure
    object PersistenceFailed : PurchaseInvoiceMaterializationFailure
}
