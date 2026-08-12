package com.miara.cuentame.core.presentation.validation

import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.ValidationError

fun Throwable.toUserMessageRes(): Int = when (this) {
    is ValidationError -> toUserMessageRes()
    else -> R.string.error_generic
}

fun ValidationError.toUserMessageRes(): Int = when (this) {
    ValidationError.InvalidName -> R.string.error_name_empty
    ValidationError.DuplicateActiveName -> R.string.error_duplicate_name
    ValidationError.InvalidDecimal -> R.string.error_invalid_decimal
    ValidationError.InvalidUnitFactor -> R.string.error_generic
    ValidationError.IncompatibleUnitDimensions -> R.string.error_dimension_mismatch
    ValidationError.MissingBaseUnitOption -> R.string.error_generic
    ValidationError.MultipleBaseUnitOptions -> R.string.error_generic
    ValidationError.InvalidBaseUnitFactor -> R.string.error_generic
    ValidationError.InvalidDefaultUnitOption -> R.string.error_default_selection
    ValidationError.IngredientHasInventoryHistory -> R.string.error_generic
    ValidationError.IngredientBaseUnitImmutable -> R.string.error_base_unit_immutable
    ValidationError.ArchivedReference -> R.string.error_generic
    ValidationError.RecordNotFound -> R.string.error_generic
    ValidationError.RestaurantNotFound -> R.string.error_no_restaurant
    ValidationError.IngredientNotFound -> R.string.error_ingredient_not_found
    ValidationError.UnitOptionNotFound -> R.string.error_unit_option_not_found
    ValidationError.UnitOptionNameAlreadyExists -> R.string.error_duplicate_unit_option
    ValidationError.BaseUnitOptionCannotBeModified -> R.string.error_base_option_modify
    ValidationError.BaseUnitOptionCannotBeArchived -> R.string.error_base_option_archive
    ValidationError.DefaultUnitOptionCannotBeArchived -> R.string.error_default_option_archive
    ValidationError.StandardUnitAlreadyAdded -> R.string.error_standard_unit_exists
    ValidationError.InvalidStandardUnitFactor -> R.string.error_standard_factor_mismatch
    ValidationError.InvalidPackageQuantity -> R.string.error_invalid_package_qty
    ValidationError.InvalidCurrencyCode -> R.string.error_invalid_currency
    ValidationError.IngredientIdAlreadyExists -> R.string.error_duplicate_ingredient
    ValidationError.UnitOptionIdAlreadyExists -> R.string.error_generic
    ValidationError.IngredientOwnershipMismatch -> R.string.error_generic
    ValidationError.InvalidBaseUnitOption -> R.string.error_generic
    ValidationError.AdditionalOptionCannotBeBase -> R.string.error_generic
    ValidationError.IngredientIsRecipeOutput -> R.string.error_ingredient_is_recipe_output
    ValidationError.IngredientUsedByRecipe -> R.string.error_ingredient_used_by_recipe
    ValidationError.UnitOptionUsedByRecipe -> R.string.error_unit_option_used_by_recipe
    ValidationError.UnitOptionUsedByRecipeComponent -> R.string.error_unit_option_used_by_recipe_comp
    
    ValidationError.SupplierNotFound -> R.string.error_supplier_not_found
    ValidationError.SupplierNameAlreadyExists -> R.string.error_supplier_exists
    ValidationError.SupplierOwnershipMismatch -> R.string.error_supplier_ownership
    ValidationError.SupplierArchived -> R.string.error_supplier_archived
    ValidationError.InvalidSupplierEmail -> R.string.error_invalid_email

    ValidationError.PurchaseNotFound -> R.string.error_purchase_not_found
    ValidationError.PurchaseLineNotFound -> R.string.error_purchase_line_not_found
    ValidationError.PurchaseOwnershipMismatch -> R.string.error_purchase_ownership
    ValidationError.PurchaseLineOwnershipMismatch -> R.string.error_purchase_ownership
    ValidationError.PurchaseNotDraft -> R.string.error_purchase_not_draft
    ValidationError.PurchaseAlreadyPosted -> R.string.error_purchase_already_posted
    ValidationError.PurchaseAlreadyVoided -> R.string.error_purchase_already_voided
    ValidationError.PurchaseHasNoLines -> R.string.error_purchase_no_lines
    ValidationError.PostedPurchaseImmutable -> R.string.error_generic
    ValidationError.VoidedPurchaseImmutable -> R.string.error_generic
    ValidationError.InvalidPurchaseQuantity -> R.string.error_quantity_positive
    ValidationError.InvalidPurchaseLineTotal -> R.string.error_total_negative
    ValidationError.InvalidPurchaseUnitOption -> R.string.error_generic
    ValidationError.InvalidPurchaseArea -> R.string.error_generic
    ValidationError.InvalidPurchaseIngredient -> R.string.error_generic
    ValidationError.InvalidPurchaseStatusTransition -> R.string.error_invalid_status_transition
    ValidationError.MalformedPurchaseMovementHistory -> R.string.error_malformed_history
    ValidationError.PurchaseMovementAlreadyExists -> R.string.error_generic
    ValidationError.PurchaseReversalAlreadyExists -> R.string.error_generic

    ValidationError.SetupAlreadyCompleted -> R.string.error_generic
    ValidationError.NoActiveInventoryArea -> R.string.error_no_areas
    ValidationError.UnsupportedLocale -> R.string.error_unsupported_locale
    ValidationError.InvalidSetupState -> R.string.error_generic
    ValidationError.FinalAreaCannotBeArchived -> R.string.error_final_area
    ValidationError.OnboardingDraftCorrupted -> R.string.error_draft_corrupted
    ValidationError.UnsupportedOnboardingDraftVersion -> R.string.error_generic
    ValidationError.OnboardingDraftSaveFailed -> R.string.error_draft_save

    ValidationError.MovementNotFound -> R.string.error_generic
    ValidationError.MovementAlreadyReversed -> R.string.error_generic
    ValidationError.CannotReverseReversal -> R.string.error_generic
    ValidationError.InvalidReversalReference -> R.string.error_generic
    ValidationError.InvalidReversalQuantity -> R.string.error_generic
    ValidationError.InvalidMovementSourceOperation -> R.string.error_generic
    ValidationError.MovementOwnershipMismatch -> R.string.error_generic

    ValidationError.StockCountNotFound -> R.string.error_count_not_found
    ValidationError.StockCountAreaNotFound -> R.string.error_count_area_not_found
    ValidationError.StockCountLineNotFound -> R.string.error_generic
    ValidationError.StockCountOwnershipMismatch -> R.string.error_count_ownership_mismatch
    ValidationError.StockCountAreaOwnershipMismatch -> R.string.error_area_ownership_mismatch
    ValidationError.StockCountLineOwnershipMismatch -> R.string.error_generic
    ValidationError.StockCountNotDraft -> R.string.error_generic
    ValidationError.StockCountAlreadyCompleted -> R.string.error_generic
    ValidationError.StockCountAlreadyVoided -> R.string.error_generic
    ValidationError.StockCountHasNoAreas -> R.string.error_generic
    ValidationError.StockCountHasNoLines -> R.string.error_generic
    ValidationError.StockCountAreaAlreadyInDraft -> R.string.error_overlapping_area
    ValidationError.StockCountAreaNotCompleted -> R.string.error_generic
    ValidationError.StockCountAreasIncomplete -> R.string.error_generic
    ValidationError.DuplicateIngredientInCountArea -> R.string.error_generic
    ValidationError.InvalidCountQuantity -> R.string.error_generic
    ValidationError.InvalidCountUnitOption -> R.string.error_generic
    ValidationError.InvalidCountIngredient -> R.string.error_generic
    ValidationError.InvalidCountArea -> R.string.error_generic
    ValidationError.InvalidCountEffectiveTime -> R.string.error_generic
    ValidationError.CompletedStockCountImmutable -> R.string.error_generic
    ValidationError.VoidedStockCountImmutable -> R.string.error_generic
    ValidationError.MalformedStockCountMovementHistory -> R.string.error_malformed_history
    ValidationError.MalformedInventoryMovementHistory -> R.string.error_malformed_history
    ValidationError.StockCountMovementAlreadyExists -> R.string.error_generic
    ValidationError.StockCountReversalAlreadyExists -> R.string.error_generic
    ValidationError.PendingCountSaves -> R.string.error_generic
    ValidationError.StockCountInventoryChanged -> R.string.error_count_inventory_changed

    ValidationError.WasteEventNotFound -> R.string.error_waste_not_found
    ValidationError.WasteEventOwnershipMismatch -> R.string.error_waste_ownership
    ValidationError.WasteEventNotDraft -> R.string.error_waste_not_draft
    ValidationError.WasteEventNotPosted -> R.string.error_generic
    ValidationError.WasteEventAlreadyPosted -> R.string.error_waste_already_posted
    ValidationError.WasteEventAlreadyVoided -> R.string.error_waste_already_voided
    ValidationError.WasteEventImmutable -> R.string.error_waste_immutable
    ValidationError.WasteIngredientNotFound -> R.string.error_generic
    ValidationError.WasteIngredientOwnershipMismatch -> R.string.error_generic
    ValidationError.WasteIngredientInactive -> R.string.error_ingredient_inactive
    ValidationError.WasteAreaNotFound -> R.string.error_generic
    ValidationError.WasteAreaOwnershipMismatch -> R.string.error_generic
    ValidationError.WasteAreaInactive -> R.string.error_area_inactive
    ValidationError.WasteUnitOptionNotFound -> R.string.error_generic
    ValidationError.WasteUnitOptionOwnershipMismatch -> R.string.error_generic
    ValidationError.WasteUnitOptionInactive -> R.string.error_unit_inactive
    ValidationError.InvalidWasteQuantity -> R.string.error_generic
    ValidationError.InvalidWasteReason -> R.string.error_reason_required
    ValidationError.InvalidWasteEffectiveTime -> R.string.error_future_effective_time
    ValidationError.WasteMovementAlreadyExists -> R.string.error_generic
    ValidationError.WasteReversalAlreadyExists -> R.string.error_generic
    ValidationError.MalformedWasteMovementHistory -> R.string.error_malformed_history
    ValidationError.WasteAttachmentUnavailable -> R.string.error_attachment_unavailable

    // Production Batches
    ValidationError.MalformedProductionMovementHistory -> R.string.error_malformed_history
    ValidationError.IngredientUsedByProductionDraft -> R.string.error_generic
    ValidationError.UnitOptionUsedByProductionDraft -> R.string.error_generic
    ValidationError.AreaUsedByProductionDraft -> R.string.error_generic

    // Invoices and OCR
    ValidationError.ParseResultChanged -> R.string.error_generic
    ValidationError.InvalidLineIndex -> R.string.error_generic
    ValidationError.InvalidMatchStatus -> R.string.error_generic
    ValidationError.InvoiceSourceLocked -> R.string.ocr_materialization_error_source_locked
}
