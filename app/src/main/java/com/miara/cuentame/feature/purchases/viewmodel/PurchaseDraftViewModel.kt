package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.CreatePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.GetPurchaseReceiptUseCase
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.domain.usecase.PostPurchaseUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePurchaseDraftUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.supplier.Supplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class PurchaseDraftUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val receiptId: PurchaseReceiptId? = null,
    val details: PurchaseDetails? = null,
    val suppliers: List<Supplier> = emptyList(),
    val error: Throwable? = null
)

sealed interface PurchaseDraftEvent {
    data class Created(val receiptId: PurchaseReceiptId) : PurchaseDraftEvent
    data object Posted : PurchaseDraftEvent
    data object Deleted : PurchaseDraftEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseDraftViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePurchaseDetailsUseCase: ObservePurchaseDetailsUseCase,
    private val getPurchaseReceiptUseCase: GetPurchaseReceiptUseCase,
    private val createPurchaseDraftUseCase: CreatePurchaseDraftUseCase,
    private val updatePurchaseDraftUseCase: UpdatePurchaseDraftUseCase,
    private val deletePurchaseDraftUseCase: DeletePurchaseDraftUseCase,
    private val deletePurchaseLineUseCase: DeletePurchaseLineUseCase,
    private val postPurchaseUseCase: PostPurchaseUseCase,
    private val observeSuppliersUseCase: ObserveSuppliersUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val receiptIdString: String? = savedStateHandle["purchaseId"]
    private val receiptId = receiptIdString?.let { PurchaseReceiptId(it) }

    private val _isSaving = MutableStateFlow(false)
    private val _isPosting = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<PurchaseDraftEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val suppliers = restaurantIdFlow.flatMapLatest { rid ->
        observeSuppliersUseCase(rid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val detailsFlow = if (receiptId == null) {
        kotlinx.coroutines.flow.flowOf(null)
    } else {
        observePurchaseDetailsUseCase(receiptId)
    }

    val uiState: StateFlow<PurchaseDraftUiState> = combine(
        detailsFlow,
        suppliers,
        _isSaving,
        _isPosting,
        _error
    ) { details, suppliers, saving, posting, error ->
        PurchaseDraftUiState(
            isLoading = receiptId != null && details == null,
            isSaving = saving,
            isPosting = posting,
            receiptId = receiptId,
            details = details,
            suppliers = suppliers,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseDraftUiState()
    )

    fun onSaveHeader(
        supplierId: SupplierId?,
        invoiceNumber: String?,
        purchaseDate: Instant,
        notes: String?
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                if (receiptId == null) {
                    val restaurant = restaurantRepository.getRestaurant() ?: throw Exception("No restaurant")
                    val newId = createPurchaseDraftUseCase(
                        CreatePurchaseDraftCommand(
                            restaurantId = restaurant.id,
                            supplierId = supplierId,
                            invoiceNumber = invoiceNumber,
                            purchaseDate = purchaseDate,
                            notes = notes
                        )
                    )
                    _events.send(PurchaseDraftEvent.Created(newId))
                } else {
                    updatePurchaseDraftUseCase(
                        UpdatePurchaseDraftCommand(
                            receiptId = receiptId,
                            supplierId = supplierId,
                            invoiceNumber = invoiceNumber,
                            purchaseDate = purchaseDate,
                            notes = notes
                        )
                    )
                }
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onDeleteLine(lineId: PurchaseLineId) {
        val rid = receiptId ?: return
        viewModelScope.launch {
            try {
                deletePurchaseLineUseCase(rid, lineId)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun onPost() {
        val rid = receiptId ?: return
        if (_isPosting.value) return
        _isPosting.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                postPurchaseUseCase(rid)
                _events.send(PurchaseDraftEvent.Posted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isPosting.value = false
            }
        }
    }

    fun onDeleteDraft() {
        val rid = receiptId ?: return
        viewModelScope.launch {
            try {
                deletePurchaseDraftUseCase(rid)
                _events.send(PurchaseDraftEvent.Deleted)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
