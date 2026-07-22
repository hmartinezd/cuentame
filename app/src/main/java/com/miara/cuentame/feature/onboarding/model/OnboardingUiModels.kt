package com.miara.cuentame.feature.onboarding.model

data class SelectableTemplateUiModel(
    val key: String,
    val labelResId: Int,
    val isSelected: Boolean
)

data class EditableNameUiModel(
    val id: String,
    val name: String
)
