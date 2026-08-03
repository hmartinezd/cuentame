package com.miara.cuentame.feature.production.viewmodel

import com.miara.cuentame.core.presentation.ui.UiMessage

sealed interface ProductionBatchScreenState {
    data object Loading : ProductionBatchScreenState
    data object Ready : ProductionBatchScreenState
    data object InvalidRoute : ProductionBatchScreenState
    data object BatchNotFound : ProductionBatchScreenState
    data object ComponentNotFound : ProductionBatchScreenState
    data object ParentNotEditable : ProductionBatchScreenState

    data class LoadError(
        val message: UiMessage
    ) : ProductionBatchScreenState
}
