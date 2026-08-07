package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerFailure
import com.miara.cuentame.core.backup.api.StoredPurchaseDocument
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
import com.miara.cuentame.core.domain.usecase.purchase.AttachPurchaseDocumentUseCase
import com.miara.cuentame.core.domain.usecase.purchase.RemovePurchaseDocumentUseCase
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

data class PurchaseDraftUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val isDeletingDraft: Boolean = false,
    val captureState: InvoiceCaptureState = InvoiceCaptureState.Idle,
    val isRemovingDocument: Boolean = false,
    val documentMetadata: StoredPurchaseDocument? = null,
    val deletingLineId: PurchaseLineId? = null,
    val currencyCode: String = "",
    val receiptId: PurchaseReceiptId? = null,
    val details: PurchaseDetails? = null,
    val suppliers: List<Supplier> = emptyList(),
    val error: Throwable? = null,
    val scannerError: PurchaseInvoiceScannerFailure? = null
)

sealed interface InvoiceCaptureState {
    data object Idle : InvoiceCaptureState
    data object PreparingScanner : InvoiceCaptureState
    data object ScannerOpen : InvoiceCaptureState
    data object ImportingScan : InvoiceCaptureState
    data object ImportingFile : InvoiceCaptureState
}

sealed interface PurchaseDraftEvent {
    data class Created(val receiptId: PurchaseReceiptId) : PurchaseDraftEvent
    data object Posted : PurchaseDraftEvent
    data object Deleted : PurchaseDraftEvent
    data class LineDeleted(val lineId: PurchaseLineId) : PurchaseDraftEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseDraftViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val createPurchaseDraftUseCase: CreatePurchaseDraftUseCase,
    private val updatePurchaseDraftUseCase: UpdatePurchaseDraftUseCase,
    private val deletePurchaseDraftUseCase: DeletePurchaseDraftUseCase,
    private val postPurchaseUseCase: PostPurchaseUseCase,
    private val deletePurchaseLineUseCase: DeletePurchaseLineUseCase,
    private val observePurchaseDetailsUseCase: ObservePurchaseDetailsUseCase,
    private val observeSuppliersUseCase: ObserveSuppliersUseCase,
    private val attachPurchaseDocumentUseCase: AttachPurchaseDocumentUseCase,
    private val removePurchaseDocumentUseCase: RemovePurchaseDocumentUseCase,
    private val documentStore: PurchaseDocumentStore,
    private val restaurantRepository: RestaurantRepository,
    private val invoiceScanner: PurchaseInvoiceScanner
) : ViewModel() {

    private val receiptId: PurchaseReceiptId? 
        get() = savedStateHandle.get<String>("receiptId")?.let { PurchaseReceiptId(it) }

    private val _isSaving = MutableStateFlow(false)
    private val _isPosting = MutableStateFlow(false)
    private val _isDeletingDraft = MutableStateFlow(false)
    private val _captureState = MutableStateFlow<InvoiceCaptureState>(InvoiceCaptureState.Idle)
    private val _isRemovingDocument = MutableStateFlow(false)
    private val _deletingLineId = MutableStateFlow<PurchaseLineId?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _scannerError = MutableStateFlow<PurchaseInvoiceScannerFailure?>(null)
    private val operationMutex = Mutex()

    private val _events = Channel<PurchaseDraftEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val restaurantFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()

    val suppliers = restaurantFlow.flatMapLatest { res ->
        observeSuppliersUseCase(res.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val detailsFlow = savedStateHandle.getStateFlow<String?>("receiptId", null)
        .flatMapLatest { idStr ->
            if (idStr == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                observePurchaseDetailsUseCase(PurchaseReceiptId(idStr))
            }
        }

    private val documentMetadataFlow = detailsFlow.flatMapLatest { details ->
        flow {
            val path = details?.receipt?.attachmentPath
            if (path == null) {
                emit(null)
            } else {
                val inspected = documentStore.inspect(path)
                val persistedName = details.receipt.attachmentDisplayName
                emit(inspected?.copy(
                    displayName = persistedName?.takeIf { it.isNotBlank() } ?: inspected.displayName
                ))
            }
        }
    }

    val uiState: StateFlow<PurchaseDraftUiState> = combine(
        detailsFlow,
        suppliers,
        restaurantFlow,
        documentMetadataFlow,
        _isSaving,
        _isPosting,
        _isDeletingDraft,
        _captureState,
        _isRemovingDocument,
        _deletingLineId,
        _error,
        _scannerError
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val details = args[0] as PurchaseDetails?
        @Suppress("UNCHECKED_CAST")
        val suppliers = args[1] as List<Supplier>
        val restaurant = args[2] as com.miara.cuentame.core.model.restaurant.Restaurant
        val documentMetadata = args[3] as StoredPurchaseDocument?
        val saving = args[4] as Boolean
        val posting = args[5] as Boolean
        val deletingDraft = args[6] as Boolean
        val captureState = args[7] as InvoiceCaptureState
        val removingDocument = args[8] as Boolean
        val deletingLineId = args[9] as PurchaseLineId?
        val error = args[10] as Throwable?
        val scannerError = args[11] as PurchaseInvoiceScannerFailure?

        PurchaseDraftUiState(
            isLoading = receiptId != null && details == null,
            isSaving = saving,
            isPosting = posting,
            isDeletingDraft = deletingDraft,
            captureState = captureState,
            isRemovingDocument = removingDocument,
            documentMetadata = documentMetadata,
            deletingLineId = deletingLineId,
            currencyCode = restaurant.currencyCode,
            receiptId = receiptId,
            details = details,
            suppliers = suppliers,
            error = error,
            scannerError = scannerError
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
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _isSaving.value = true
                _error.value = null
                
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
                operationMutex.unlock()
            }
        }
    }

    fun onAttachDocument(uri: android.net.Uri) {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _captureState.value = InvoiceCaptureState.ImportingFile
                _error.value = null
                attachPurchaseDocumentUseCase(currentReceiptId, uri)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _captureState.value = InvoiceCaptureState.Idle
                operationMutex.unlock()
            }
        }
    }

    fun onPrepareScanner(activity: android.app.Activity, onIntentReady: (android.content.IntentSender) -> Unit) {
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _captureState.value = InvoiceCaptureState.PreparingScanner
                _scannerError.value = null
                val intentSender = invoiceScanner.getStartScanIntent(activity)
                _captureState.value = InvoiceCaptureState.ScannerOpen
                onIntentReady(intentSender)
            } catch (e: Exception) {
                _scannerError.value = PurchaseInvoiceScannerFailure.LaunchFailed
                _captureState.value = InvoiceCaptureState.Idle
                operationMutex.unlock() // Unlocking early on failure
            }
            // If success, we don't unlock here; it stays locked until result or cancelled
        }
    }

    fun onScannerResult(resultCode: Int, data: android.content.Intent?) {
        val currentReceiptId = receiptId ?: run {
             operationMutex.unlock()
             _captureState.value = InvoiceCaptureState.Idle
             return
        }
        viewModelScope.launch {
            try {
                val result = invoiceScanner.parseResult(resultCode, data)
                when (result) {
                    is PurchaseInvoiceScanResult.Success -> {
                        _captureState.value = InvoiceCaptureState.ImportingScan
                        val dateStr = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(java.time.LocalDate.now())
                        val displayName = "Scanned invoice - $dateStr.pdf"
                        attachPurchaseDocumentUseCase(currentReceiptId, result.pdfUri, displayName)
                    }
                    is PurchaseInvoiceScanResult.Failure -> {
                        _scannerError.value = result.reason
                    }
                    PurchaseInvoiceScanResult.Cancelled -> {
                        // Silent
                    }
                }
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _captureState.value = InvoiceCaptureState.Idle
                if (operationMutex.isLocked) {
                    operationMutex.unlock()
                }
            }
        }
    }

    fun onScannerCancelled() {
        if (operationMutex.isLocked) {
            operationMutex.unlock()
        }
        _captureState.value = InvoiceCaptureState.Idle
    }

    fun onRemoveDocument() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _isRemovingDocument.value = true
                _error.value = null
                removePurchaseDocumentUseCase(currentReceiptId)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isRemovingDocument.value = false
                operationMutex.unlock()
            }
        }
    }

    fun onPost() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _isPosting.value = true
                _error.value = null
                postPurchaseUseCase(currentReceiptId)
                _events.send(PurchaseDraftEvent.Posted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isPosting.value = false
                operationMutex.unlock()
            }
        }
    }

    fun onDeleteDraft() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _isDeletingDraft.value = true
                _error.value = null
                deletePurchaseDraftUseCase(currentReceiptId)
                _events.send(PurchaseDraftEvent.Deleted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isDeletingDraft.value = false
                operationMutex.unlock()
            }
        }
    }

    fun onDeleteLine(lineId: PurchaseLineId) {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!operationMutex.tryLock()) return@launch
            try {
                _deletingLineId.value = lineId
                _error.value = null
                deletePurchaseLineUseCase(currentReceiptId, lineId)
                _events.send(PurchaseDraftEvent.LineDeleted(lineId))
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _deletingLineId.value = null
                operationMutex.unlock()
            }
        }
    }

    fun clearError() {
        _error.value = null
        _scannerError.value = null
    }
}
