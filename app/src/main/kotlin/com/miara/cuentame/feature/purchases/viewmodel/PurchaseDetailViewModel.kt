package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.VoidPurchaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PurchaseDetailState {
    data object Loading : PurchaseDetailState
    data object NotFound : PurchaseDetailState
    data class Ready(val details: PurchaseDetails) : PurchaseDetailState
    data class Error(val throwable: Throwable) : PurchaseDetailState
}

data class PurchaseDetailUiState(
    val state: PurchaseDetailState = PurchaseDetailState.Loading,
    val currencyCode: String = "",
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
    private val voidPurchaseUseCase: VoidPurchaseUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val purchaseIdStr: String? = savedStateHandle["purchaseId"]
    private val purchaseId = purchaseIdStr?.let { PurchaseReceiptId(it) }

    private val _isVoiding = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<PurchaseDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val detailsFlow = if (purchaseId == null) {
        kotlinx.coroutines.flow.flowOf(null)
    } else {
        observePurchaseDetailsUseCase(purchaseId)
    }
    
    private val restaurantFlow = restaurantRepository.observeRestaurant().filterNotNull()

    val uiState: StateFlow<PurchaseDetailUiState> = combine(
        detailsFlow,
        restaurantFlow,
        _isVoiding,
        _error
    ) { details, restaurant, voiding, error ->
        val state = when {
            purchaseId == null -> PurchaseDetailState.Error(Exception("Invalid purchase ID"))
            details != null -> PurchaseDetailState.Ready(details)
            else -> PurchaseDetailState.NotFound
        }
        PurchaseDetailUiState(
            state = state,
            currencyCode = restaurant.currencyCode,
            isVoiding = voiding,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseDetailUiState()
    )

    fun onVoid() {
        if (purchaseId == null || _isVoiding.value) return
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
