package com.venkoi.cuentame.core.database.repository

object IntegrationFailurePoints {

    const val WASTE_DELETE_AFTER_VALIDATION = "waste-delete-after-validation"

    const val WASTE_POST_AFTER_MARK_POSTED = "waste-post-after-mark-posted"

    const val WASTE_POST_AFTER_MOVEMENT = "waste-post-after-movement"

    const val WASTE_POST_AFTER_PROJECTION = "waste-post-after-projection"

    const val WASTE_VOID_AFTER_MARK_VOIDED = "waste-void-after-mark-voided"

    const val WASTE_VOID_AFTER_REVERSAL = "waste-void-after-reversal"

    const val WASTE_VOID_AFTER_PROJECTION = "waste-void-after-projection"

    const val PURCHASE_POST_AFTER_MOVEMENTS = "purchase-post-after-movements"

    const val PURCHASE_POST_AFTER_PROJECTIONS = "purchase-post-after-projections"

    const val PURCHASE_POST_AFTER_MARK_POSTED = "purchase-post-after-mark-posted"

    const val PURCHASE_VOID_AFTER_REVERSALS = "purchase-void-after-reversals"

    const val PURCHASE_VOID_AFTER_PROJECTIONS = "purchase-void-after-projections"

    const val PURCHASE_VOID_AFTER_MARK_VOIDED = "purchase-void-after-mark-voided"

    const val PURCHASE_MATERIALIZATION_AFTER_START = "purchase-materialization-after-start"

    const val PRODUCTION_POST_AFTER_SNAPSHOTS = "production-post-after-snapshots"
    const val PRODUCTION_POST_AFTER_CONSUMPTION = "production-post-after-consumption"
    const val PRODUCTION_POST_AFTER_OUTPUT = "production-post-after-output"
    const val PRODUCTION_POST_AFTER_PROJECTIONS = "production-post-after-projections"
    const val PRODUCTION_POST_AFTER_MARK_POSTED = "production-post-after-mark-posted"

    const val PRODUCTION_VOID_AFTER_REVERSALS = "production-void-after-reversals"
    const val PRODUCTION_VOID_AFTER_PROJECTIONS = "production-void-after-projections"
    const val PRODUCTION_VOID_AFTER_MARK_VOIDED = "production-void-after-mark-voided"
}
