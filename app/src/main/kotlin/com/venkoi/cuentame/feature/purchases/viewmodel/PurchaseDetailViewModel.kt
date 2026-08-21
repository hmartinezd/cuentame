package com.venkoi.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.core.backup.api.PurchaseDocumentStore
import com.venkoi.cuentame.core.backup.api.StoredPurchaseDocument
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.domain.repository.PurchaseDetails
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.venkoi.cuentame.core.domain.usecase.VoidPurchaseUseCase
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    val documentMetadata: StoredPurchaseDocument? = null,
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
    private val documentStore: PurchaseDocumentStore,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val receiptIdStr: String? = savedStateHandle["receiptId"]
    private val receiptId = receiptIdStr?.let { PurchaseReceiptId(it) }

    private val _isVoiding = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<PurchaseDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val detailsFlow = if (receiptId == null) {
        kotlinx.coroutines.flow.flowOf(null)
    } else {
        observePurchaseDetailsUseCase(receiptId)
    }
    
    private val restaurantFlow = restaurantRepository.observeRestaurant().filterNotNull()

    private val documentMetadataFlow = detailsFlow.flatMapLatest { details ->
        flow {
            val path = details?.receipt?.attachmentPath
            val displayName = details?.receipt?.attachmentDisplayName
            emit(path?.let { p ->
                documentStore.inspect(p)?.let { metadata ->
                    if (displayName != null) {
                        metadata.copy(displayName = displayName)
                    } else {
                        metadata
                    }
                }
            })
        }
    }

    val uiState: StateFlow<PurchaseDetailUiState> = combine(
        detailsFlow,
        restaurantFlow,
        documentMetadataFlow,
        _isVoiding,
        _error
    ) { details, restaurant, documentMetadata, voiding, error ->
        val state = when {
            receiptId == null -> PurchaseDetailState.Error(Exception("Invalid purchase ID"))
            details != null -> PurchaseDetailState.Ready(details)
            else -> PurchaseDetailState.NotFound
        }
        PurchaseDetailUiState(
            state = state,
            currencyCode = restaurant.currencyCode,
            isVoiding = voiding,
            documentMetadata = documentMetadata,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseDetailUiState()
    )

    fun onVoid() {
        if (receiptId == null || _isVoiding.value) return
        
        val currentState = uiState.value.state
        if (currentState !is PurchaseDetailState.Ready || currentState.details.receipt.status != DocumentStatus.POSTED) {
            return
        }

        _isVoiding.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                voidPurchaseUseCase(receiptId)
                _events.send(PurchaseDetailEvent.Voided)
            } catch (e: CancellationException) {
                throw e
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
