package com.miara.cuentame.core.backup.internal

object IntegrationFailurePoints {

    const val WASTE_DELETE_AFTER_VALIDATION = "delete-after-validation"

    const val WASTE_POST_AFTER_MARK_POSTED = "post-after-mark-posted"

    const val WASTE_POST_AFTER_MOVEMENT = "post-after-movement"

    const val WASTE_POST_AFTER_PROJECTION = "post-after-projection"

    const val WASTE_VOID_AFTER_MARK_VOIDED = "void-after-mark-voided"

    const val WASTE_VOID_AFTER_REVERSAL = "void-after-reversal"

    const val WASTE_VOID_AFTER_PROJECTION = "void-after-projection"

    const val PURCHASE_POST_AFTER_MARK_POSTED = "purchase-post-after-mark-posted"

    const val PURCHASE_POST_AFTER_MOVEMENTS = "purchase-post-after-movements"

    const val PURCHASE_POST_AFTER_PROJECTIONS = "purchase-post-after-projections"
    
    const val PURCHASE_VOID_AFTER_MARK_VOIDED = "purchase-void-after-mark-voided"
}
