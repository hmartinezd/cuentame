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
            ValidationError.RecordNotFound -> R.string.error_generic
            else -> R.string.error_generic
        }
        else -> R.string.error_generic
    }
}
