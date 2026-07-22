package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.VoidPurchaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val details: PurchaseDetails? = null,
    val isVoiding: Boolean = false,
    val error: Throwable? = null
)

sealed interface PurchaseDetailEvent {
    data object Voided : PurchaseDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePurchaseDetailsUseCase: ObservePurchaseDetailsUseCase,
    private val voidPurchaseUseCase: VoidPurchaseUseCase
) : ViewModel() {

    private val purchaseId = PurchaseReceiptId(requireNotNull(savedStateHandle.get<String>("purchaseId")))

    private val _isVoiding = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<PurchaseDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<PurchaseDetailUiState> = combine(
        observePurchaseDetailsUseCase(purchaseId),
        _isVoiding,
        _error
    ) { details, voiding, error ->
        PurchaseDetailUiState(
            isLoading = details == null,
            details = details,
            isVoiding = voiding,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseDetailUiState()
    )

    fun onVoid() {
        if (_isVoiding.value) return
        _isVoiding.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                voidPurchaseUseCase(purchaseId)
                _events.send(PurchaseDetailEvent.Voided)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isVoiding.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
