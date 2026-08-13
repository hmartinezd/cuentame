package com.miara.cuentame.core.model.purchase.materialization.failure

import com.miara.cuentame.core.model.purchase.DuplicateInvoiceCandidate

sealed interface PurchaseInvoiceMaterializationResult {
    data object Success : PurchaseInvoiceMaterializationResult
    data class Failure(val reason: PurchaseInvoiceMaterializationFailure) : PurchaseInvoiceMaterializationResult
}

sealed interface PurchaseInvoiceMaterializationFailure {
    data object PurchaseNotFound : PurchaseInvoiceMaterializationFailure
    data object PurchaseAlreadyPosted : PurchaseInvoiceMaterializationFailure
    data object DocumentMissing : PurchaseInvoiceMaterializationFailure
    data object DocumentChanged : PurchaseInvoiceMaterializationFailure
    data object ParseChanged : PurchaseInvoiceMaterializationFailure
    data object InvoiceStateChanged : PurchaseInvoiceMaterializationFailure
    data object SupplierChanged : PurchaseInvoiceMaterializationFailure
    data object UnresolvedLines : PurchaseInvoiceMaterializationFailure
    data object InvalidConfirmedMatch : PurchaseInvoiceMaterializationFailure
    data object MissingQuantity : PurchaseInvoiceMaterializationFailure
    data object MissingRequiredUnitOption : PurchaseInvoiceMaterializationFailure
    data object MissingRequiredArea : PurchaseInvoiceMaterializationFailure
    data object DraftChanged : PurchaseInvoiceMaterializationFailure
    data object ManualEditConflict : PurchaseInvoiceMaterializationFailure
    data object InvoiceSourceLocked : PurchaseInvoiceMaterializationFailure
    data class StrongDuplicate(val candidate: DuplicateInvoiceCandidate) : PurchaseInvoiceMaterializationFailure
    data object PersistenceFailed : PurchaseInvoiceMaterializationFailure
}
