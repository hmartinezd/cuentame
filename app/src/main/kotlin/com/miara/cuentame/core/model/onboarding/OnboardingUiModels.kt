package com.miara.cuentame.core.model.onboarding

data class OnboardingItemUiModel(
    val id: String,
    val templateKey: String? = null,
    val labelResId: Int? = null,
    val customName: String? = null,
    val isSelected: Boolean,
    val sortOrder: Int
) {
    val isSuggested: Boolean get() = templateKey != null
}
