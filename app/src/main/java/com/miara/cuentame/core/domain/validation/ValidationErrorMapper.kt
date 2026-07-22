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
            ValidationError.MissingBaseUnitOption -> R.string.error_generic
            ValidationError.MultipleBaseUnitOptions -> R.string.error_generic
            ValidationError.InvalidBaseUnitFactor -> R.string.error_generic
            ValidationError.IncompatibleUnitDimensions -> R.string.error_dimension_mismatch
            ValidationError.IngredientIdAlreadyExists -> R.string.error_generic // UI usually generates these
            ValidationError.UnitOptionIdAlreadyExists -> R.string.error_generic
            ValidationError.IngredientOwnershipMismatch -> R.string.error_generic
            ValidationError.RecordNotFound -> R.string.error_generic
            else -> R.string.error_generic
        }
        else -> R.string.error_generic
    }
}
