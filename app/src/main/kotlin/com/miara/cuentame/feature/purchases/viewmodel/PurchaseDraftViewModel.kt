package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.CreatePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.domain.usecase.PostPurchaseUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePurchaseDraftUseCase
import com.miara.cuentame.core.model.supplier.Supplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class PurchaseDraftUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val isDeletingDraft: Boolean = false,
    val deletingLineId: PurchaseLineId? = null,
    val currencyCode: String = "",
    val receiptId: PurchaseReceiptId? = null,
    val details: PurchaseDetails? = null,
    val suppliers: List<Supplier> = emptyList(),
    val error: Throwable? = null
)

sealed interface PurchaseDraftEvent {
    data class Created(val receiptId: PurchaseReceiptId) : PurchaseDraftEvent
    data object Posted : PurchaseDraftEvent
    data object Deleted : PurchaseDraftEvent
    data class LineDeleted(val lineId: PurchaseLineId) : PurchaseDraftEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseDraftViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createPurchaseDraftUseCase: CreatePurchaseDraftUseCase,
    private val updatePurchaseDraftUseCase: UpdatePurchaseDraftUseCase,
    private val deletePurchaseDraftUseCase: DeletePurchaseDraftUseCase,
    private val postPurchaseUseCase: PostPurchaseUseCase,
    private val deletePurchaseLineUseCase: DeletePurchaseLineUseCase,
    private val observePurchaseDetailsUseCase: ObservePurchaseDetailsUseCase,
    private val observeSuppliersUseCase: ObserveSuppliersUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val purchaseIdStr: String? = savedStateHandle["purchaseId"]
    private val receiptId = purchaseIdStr?.let { PurchaseReceiptId(it) }

    private val _isSaving = MutableStateFlow(false)
    private val _isPosting = MutableStateFlow(false)
    private val _isDeletingDraft = MutableStateFlow(false)
    private val _deletingLineId = MutableStateFlow<PurchaseLineId?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<PurchaseDraftEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val restaurantFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()

    val suppliers = restaurantFlow.flatMapLatest { res ->
        observeSuppliersUseCase(res.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val detailsFlow = if (receiptId == null) {
        kotlinx.coroutines.flow.flowOf(null)
    } else {
        observePurchaseDetailsUseCase(receiptId)
    }

    val uiState: StateFlow<PurchaseDraftUiState> = combine(
        detailsFlow,
        suppliers,
        restaurantFlow,
        _isSaving,
        _isPosting,
        _isDeletingDraft,
        _deletingLineId,
        _error
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val details = args[0] as PurchaseDetails?
        @Suppress("UNCHECKED_CAST")
        val suppliers = args[1] as List<Supplier>
        val restaurant = args[2] as com.miara.cuentame.core.model.restaurant.Restaurant
        val saving = args[3] as Boolean
        val posting = args[4] as Boolean
        val deletingDraft = args[5] as Boolean
        val deletingLineId = args[6] as PurchaseLineId?
        val error = args[7] as Throwable?

        PurchaseDraftUiState(
            isLoading = receiptId != null && details == null,
            isSaving = saving,
            isPosting = posting,
            isDeletingDraft = deletingDraft,
            deletingLineId = deletingLineId,
            currencyCode = restaurant.currencyCode,
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
                val currentState = uiState.value
                if (currentState.receiptId == null) {
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
                            receiptId = currentState.receiptId,
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

    fun onPost() {
        val currentReceiptId = receiptId ?: return
        if (_isPosting.value) return
        _isPosting.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                postPurchaseUseCase(currentReceiptId)
                _events.send(PurchaseDraftEvent.Posted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isPosting.value = false
            }
        }
    }

    fun onDeleteDraft() {
        val currentReceiptId = receiptId ?: return
        if (_isDeletingDraft.value) return
        _isDeletingDraft.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                deletePurchaseDraftUseCase(currentReceiptId)
                _events.send(PurchaseDraftEvent.Deleted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isDeletingDraft.value = false
            }
        }
    }

    fun onDeleteLine(lineId: PurchaseLineId) {
        val currentReceiptId = receiptId ?: return
        if (_deletingLineId.value != null) return
        _deletingLineId.value = lineId
        _error.value = null

        viewModelScope.launch {
            try {
                deletePurchaseLineUseCase(currentReceiptId, lineId)
                _events.send(PurchaseDraftEvent.LineDeleted(lineId))
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _deletingLineId.value = null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
