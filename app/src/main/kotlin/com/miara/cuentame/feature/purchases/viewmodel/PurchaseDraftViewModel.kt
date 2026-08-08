package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanResult
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScanner
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerFailure
import com.miara.cuentame.core.backup.api.PurchaseInvoiceScannerException
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
import com.miara.cuentame.core.domain.usecase.purchase.AnalyzePurchaseInvoiceDocumentUseCase
import com.miara.cuentame.core.domain.usecase.purchase.AnalyzePurchaseInvoiceResult
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrFailure
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.presentation.ui.findActivity
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
import java.util.UUID
import javax.inject.Inject

data class PurchaseDraftUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val isDeletingDraft: Boolean = false,
    val captureState: InvoiceCaptureState = InvoiceCaptureState.Idle,
    val isRemovingDocument: Boolean = false,
    val documentMetadata: StoredPurchaseDocument? = null,
    val ocrResult: PurchaseInvoiceOcrResult? = null,
    val parseResult: PurchaseInvoiceParseResult? = null,
    val ocrState: OcrAnalysisState = OcrAnalysisState.Idle,
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

sealed interface OcrAnalysisState {
    data object Idle : OcrAnalysisState
    data class Analyzing(val current: Int, val total: Int) : OcrAnalysisState
    data object Parsing : OcrAnalysisState
    data object Parsed : OcrAnalysisState
    data class Failure(val reason: PurchaseInvoiceOcrFailure) : OcrAnalysisState
}

sealed interface PurchaseDraftEvent {
    data class Created(val receiptId: PurchaseReceiptId) : PurchaseDraftEvent
    data object Posted : PurchaseDraftEvent
    data object Deleted : PurchaseDraftEvent
    data class LineDeleted(val lineId: PurchaseLineId) : PurchaseDraftEvent
    data class NavigateToReview(val receiptId: PurchaseReceiptId) : PurchaseDraftEvent
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
    private val analyzePurchaseInvoiceDocumentUseCase: AnalyzePurchaseInvoiceDocumentUseCase,
    private val parseUseCase: com.miara.cuentame.core.domain.usecase.purchase.ParsePurchaseInvoiceUseCase,
    private val repository: com.miara.cuentame.core.domain.repository.PurchaseRepository,
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
    private val _ocrState = MutableStateFlow<OcrAnalysisState>(OcrAnalysisState.Idle)
    private val _deletingLineId = MutableStateFlow<PurchaseLineId?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _scannerError = MutableStateFlow<PurchaseInvoiceScannerFailure?>(null)
    private val operationMutex = Mutex()

    init {
        // Handle restoration of pending scan state
        if (savedStateHandle.get<String>(KEY_PENDING_SCAN_SESSION_ID) != null) {
            _captureState.value = InvoiceCaptureState.ScannerOpen
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun canStartMutation(): Boolean {
        val captureBusy = _captureState.value != InvoiceCaptureState.Idle

        val analysisBusy = when (_ocrState.value) {
            is OcrAnalysisState.Analyzing,
            OcrAnalysisState.Parsing -> true
            else -> false
        }

        val scannerPending = savedStateHandle.get<String>(KEY_PENDING_SCAN_SESSION_ID) != null

        val localMutationBusy = _isSaving.value ||
                _isPosting.value ||
                _isDeletingDraft.value ||
                _isRemovingDocument.value ||
                _deletingLineId.value != null

        return !captureBusy && !analysisBusy && !scannerPending && !localMutationBusy
    }

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

    private val ocrResultFlow = savedStateHandle.getStateFlow<String?>("receiptId", null)
        .flatMapLatest { idStr ->
            if (idStr == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                repository.observeOcrResult(PurchaseReceiptId(idStr))
            }
        }

    private val parseResultFlow = savedStateHandle.getStateFlow<String?>("receiptId", null)
        .flatMapLatest { idStr ->
            if (idStr == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                repository.observeParseResult(PurchaseReceiptId(idStr))
            }
        }

    val uiState: StateFlow<PurchaseDraftUiState> = combine(
        detailsFlow,
        suppliers,
        restaurantFlow,
        documentMetadataFlow,
        ocrResultFlow,
        parseResultFlow,
        _isSaving,
        _isPosting,
        _isDeletingDraft,
        _captureState,
        _ocrState,
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
        val ocrResult = args[4] as PurchaseInvoiceOcrResult?
        val parseResult = args[5] as PurchaseInvoiceParseResult?
        val saving = args[6] as Boolean
        val posting = args[7] as Boolean
        val deletingDraft = args[8] as Boolean
        val captureState = args[9] as InvoiceCaptureState
        val ocrState = args[10] as OcrAnalysisState
        val removingDocument = args[11] as Boolean
        val deletingLineId = args[12] as PurchaseLineId?
        val error = args[13] as Throwable?
        val scannerError = args[14] as PurchaseInvoiceScannerFailure?

        PurchaseDraftUiState(
            isLoading = receiptId != null && details == null,
            isSaving = saving,
            isPosting = posting,
            isDeletingDraft = deletingDraft,
            captureState = captureState,
            isRemovingDocument = removingDocument,
            documentMetadata = documentMetadata,
            ocrResult = ocrResult,
            parseResult = parseResult,
            ocrState = ocrState,
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
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
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
                }
            }
        }
    }

    fun onAttachDocument(uri: android.net.Uri) {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _captureState.value = InvoiceCaptureState.ImportingFile
                    _error.value = null
                    attachPurchaseDocumentUseCase(currentReceiptId, uri)
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _captureState.value = InvoiceCaptureState.Idle
                }
            }
        }
    }

    fun onPrepareScanner(context: android.content.Context, onIntentReady: (android.content.IntentSender) -> Unit) {
        val activity = context.findActivity() ?: run {
            _scannerError.value = PurchaseInvoiceScannerFailure.Unavailable
            return
        }
        
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _captureState.value = InvoiceCaptureState.PreparingScanner
                    _scannerError.value = null
                    val intentSender = invoiceScanner.getStartScanIntent(activity)
                    
                    // Generate and persist session ID before releasing mutex
                    val sessionId = UUID.randomUUID().toString()
                    savedStateHandle[KEY_PENDING_SCAN_SESSION_ID] = sessionId
                    
                    _captureState.value = InvoiceCaptureState.ScannerOpen
                    onIntentReady(intentSender)
                } catch (e: Exception) {
                    val reason = (e as? PurchaseInvoiceScannerException)?.reason ?: PurchaseInvoiceScannerFailure.LaunchFailed
                    _scannerError.value = reason
                    _captureState.value = InvoiceCaptureState.Idle
                    savedStateHandle[KEY_PENDING_SCAN_SESSION_ID] = null
                    if (e !is PurchaseInvoiceScannerException) {
                        _error.value = e
                    }
                }
                // Mutex is released here. The pending session protects against concurrent mutations.
            }
        }
    }

    fun onScannerResult(resultCode: Int, data: android.content.Intent?) {
        val currentReceiptId = receiptId ?: run {
             consumePendingScanSession()
             _captureState.value = InvoiceCaptureState.Idle
             return
        }

        // Atomically claim the session
        if (consumePendingScanSession() == null) return

        viewModelScope.launch {
            operationMutex.withLock {
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
                }
            }
        }
    }

    private fun consumePendingScanSession(): String? {
        val id = savedStateHandle.get<String>(KEY_PENDING_SCAN_SESSION_ID)
        savedStateHandle[KEY_PENDING_SCAN_SESSION_ID] = null
        return id
    }

    fun onScannerCancelled() {
        consumePendingScanSession()
        _captureState.value = InvoiceCaptureState.Idle
    }

    fun onRemoveDocument() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _isRemovingDocument.value = true
                    _error.value = null
                    removePurchaseDocumentUseCase(currentReceiptId)
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _isRemovingDocument.value = false
                }
            }
        }
    }

    fun onPost() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _isPosting.value = true
                    _error.value = null
                    postPurchaseUseCase(currentReceiptId)
                    _events.send(PurchaseDraftEvent.Posted)
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _isPosting.value = false
                }
            }
        }
    }

    fun onDeleteDraft() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _isDeletingDraft.value = true
                    _error.value = null
                    deletePurchaseDraftUseCase(currentReceiptId)
                    _events.send(PurchaseDraftEvent.Deleted)
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _isDeletingDraft.value = false
                }
            }
        }
    }

    fun onDeleteLine(lineId: PurchaseLineId) {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _deletingLineId.value = lineId
                    _error.value = null
                    deletePurchaseLineUseCase(currentReceiptId, lineId)
                    _events.send(PurchaseDraftEvent.LineDeleted(lineId))
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _deletingLineId.value = null
                }
            }
        }
    }

    fun clearGeneralError() {
        _error.value = null
    }

    fun clearScannerError() {
        _scannerError.value = null
    }

    fun onAnalyzeInvoice() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _ocrState.value = OcrAnalysisState.Analyzing(0, 0)
                    analyzePurchaseInvoiceDocumentUseCase(currentReceiptId).collect { result ->
                        when (result) {
                            is AnalyzePurchaseInvoiceResult.Preparing -> {
                                _ocrState.value = OcrAnalysisState.Analyzing(0, 0)
                            }
                            is AnalyzePurchaseInvoiceResult.ProcessingPage -> {
                                _ocrState.value = OcrAnalysisState.Analyzing(result.current, result.total)
                            }
                            is AnalyzePurchaseInvoiceResult.Saving -> {
                                _ocrState.value = OcrAnalysisState.Analyzing(100, 100) // "Saving..."
                            }
                            AnalyzePurchaseInvoiceResult.Parsing -> {
                                _ocrState.value = OcrAnalysisState.Parsing
                            }
                            AnalyzePurchaseInvoiceResult.Success -> {
                                _ocrState.value = OcrAnalysisState.Parsed
                            }
                            is AnalyzePurchaseInvoiceResult.Failure -> {
                                _ocrState.value = OcrAnalysisState.Failure(result.reason)
                            }
                            is AnalyzePurchaseInvoiceResult.ParseFailed -> {
                                _ocrState.value = OcrAnalysisState.Idle // Text extracted, details failed
                            }
                        }
                    }
                } catch (e: Exception) {
                    _ocrState.value = OcrAnalysisState.Failure(PurchaseInvoiceOcrFailure.Unknown)
                }
            }
        }
    }

    fun onParseDetails() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            if (!canStartMutation()) return@launch
            operationMutex.withLock {
                try {
                    _ocrState.value = OcrAnalysisState.Parsing
                    val result = parseUseCase.execute(currentReceiptId)
                    if (result is com.miara.cuentame.core.domain.usecase.purchase.ParsePurchaseInvoiceResult.Success) {
                        _ocrState.value = OcrAnalysisState.Parsed
                    } else {
                        _ocrState.value = OcrAnalysisState.Failure(PurchaseInvoiceOcrFailure.ParsingFailed)
                    }
                } catch (e: Exception) {
                     _ocrState.value = OcrAnalysisState.Failure(PurchaseInvoiceOcrFailure.ParsingFailed)
                }
            }
        }
    }

    fun onReviewInvoice() {
        val currentReceiptId = receiptId ?: return
        viewModelScope.launch {
            _events.send(PurchaseDraftEvent.NavigateToReview(currentReceiptId))
        }
    }

    fun clearOcrError() {
        _ocrState.value = OcrAnalysisState.Idle
    }

    companion object {
        private const val KEY_PENDING_SCAN_SESSION_ID = "pendingInvoiceScanSessionId"
    }
}
