package com.venkoi.cuentame.feature.preparations.viewmodel

import com.venkoi.cuentame.core.presentation.ui.UiMessage

sealed interface PreparationScreenLoadState {
    data object Loading : PreparationScreenLoadState
    data object CreateReady : PreparationScreenLoadState
    data object EditReady : PreparationScreenLoadState
    data object InvalidRoute : PreparationScreenLoadState
    data object RecipeNotFound : PreparationScreenLoadState
    data object ComponentNotFound : PreparationScreenLoadState
    data object ParentNotEditable : PreparationScreenLoadState
    data class LoadError(val message: UiMessage) : PreparationScreenLoadState
}
