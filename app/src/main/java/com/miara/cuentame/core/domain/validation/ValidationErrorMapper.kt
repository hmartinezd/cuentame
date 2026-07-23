package com.miara.cuentame.core.domain.validation

import com.miara.cuentame.R

fun Throwable.toUserMessageRes(): Int {
    return when (this) {
        is ValidationError -> when (this) {
            ValidationError.InvalidName -> R.string.error_name_empty
            ValidationError.DuplicateActiveName -> R.string.error_duplicate_name
            ValidationError.InvalidCurrencyCode -> R.string.error_invalid_currency
            ValidationError.UnsupportedLocale -> R.string.error_unsupported_locale
            ValidationError.NoActiveInventoryArea -> R.string.error_no_areas
            ValidationError.FinalAreaCannotBeArchived -> R.string.error_final_area
            ValidationError.OnboardingDraftCorrupted -> R.string.error_draft_corrupted
            ValidationError.OnboardingDraftSaveFailed -> R.string.error_draft_save
            ValidationError.IngredientNotFound -> R.string.error_ingredient_not_found
            ValidationError.IngredientBaseUnitImmutable -> R.string.error_base_unit_immutable
            ValidationError.UnitOptionNotFound -> R.string.error_unit_option_not_found
            ValidationError.UnitOptionNameAlreadyExists -> R.string.error_duplicate_unit_option
            ValidationError.BaseUnitOptionCannotBeModified -> R.string.error_base_option_modify
            ValidationError.BaseUnitOptionCannotBeArchived -> R.string.error_base_option_archive
            ValidationError.DefaultUnitOptionCannotBeArchived -> R.string.error_default_option_archive
            ValidationError.StandardUnitAlreadyAdded -> R.string.error_standard_unit_exists
            ValidationError.InvalidStandardUnitFactor -> R.string.error_standard_factor_mismatch
            ValidationError.InvalidPackageQuantity -> R.string.error_invalid_package_qty
            ValidationError.InvalidDefaultUnitOption -> R.string.error_default_selection
            ValidationError.IncompatibleUnitDimensions -> R.string.error_dimension_mismatch
            
            // Suppliers
            ValidationError.SupplierNotFound -> R.string.error_supplier_not_found
            ValidationError.SupplierNameAlreadyExists -> R.string.error_supplier_exists
            ValidationError.SupplierOwnershipMismatch -> R.string.error_supplier_ownership
            ValidationError.SupplierArchived -> R.string.error_supplier_archived
            ValidationError.InvalidSupplierEmail -> R.string.error_invalid_email

            // Purchases
            ValidationError.PurchaseNotFound -> R.string.error_purchase_not_found
            ValidationError.PurchaseLineNotFound -> R.string.error_purchase_line_not_found
            ValidationError.PurchaseOwnershipMismatch -> R.string.error_purchase_ownership
            ValidationError.PurchaseLineOwnershipMismatch -> R.string.error_purchase_ownership
            ValidationError.IngredientOwnershipMismatch -> R.string.error_purchase_ownership
            ValidationError.PurchaseNotDraft -> R.string.error_purchase_not_draft
            ValidationError.PurchaseAlreadyPosted -> R.string.error_purchase_already_posted
            ValidationError.PurchaseAlreadyVoided -> R.string.error_purchase_already_voided
            ValidationError.PurchaseHasNoLines -> R.string.error_purchase_no_lines
            ValidationError.MalformedPurchaseMovementHistory -> R.string.error_malformed_history
            ValidationError.InvalidPurchaseStatusTransition -> R.string.error_invalid_status_transition
            
            ValidationError.InvalidPurchaseArea -> R.string.error_area_required
            ValidationError.InvalidPurchaseIngredient -> R.string.error_ingredient_required
            ValidationError.InvalidPurchaseUnitOption -> R.string.error_unit_required
            ValidationError.InvalidPurchaseQuantity -> R.string.error_quantity_positive
            ValidationError.InvalidPurchaseLineTotal -> R.string.error_total_negative

            else -> R.string.error_generic
        }
        else -> R.string.error_generic
    }
}
