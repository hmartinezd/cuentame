package com.venkoi.cuentame.feature.purchases.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.core.backup.PurchaseAttachmentLocation
import com.venkoi.cuentame.core.backup.api.PurchaseDocumentStore
import com.venkoi.cuentame.core.backup.api.PurchasePdfRenderer
import com.venkoi.cuentame.core.backup.api.StoredPurchaseDocument
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.domain.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PurchaseDocumentViewerState {
    data object Loading : PurchaseDocumentViewerState
    data class Ready(
        val document: StoredPurchaseDocument,
        val pageCount: Int = 0
    ) : PurchaseDocumentViewerState
    data object NotFound : PurchaseDocumentViewerState
    data class Error(
        val throwable: Throwable,
        val message: String? = null
    ) : PurchaseDocumentViewerState
}

@HiltViewModel
class PurchaseDocumentViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseRepository: PurchaseRepository,
    private val documentStore: PurchaseDocumentStore,
    private val pdfRenderer: PurchasePdfRenderer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val receiptIdStr: String? = savedStateHandle["receiptId"]
    private val receiptId = receiptIdStr?.let { PurchaseReceiptId(it) }

    private val _uiState = MutableStateFlow<PurchaseDocumentViewerState>(PurchaseDocumentViewerState.Loading)
    val uiState: StateFlow<PurchaseDocumentViewerState> = _uiState

    init {
        loadDocument()
    }

    private fun loadDocument() {
        if (receiptId == null) {
            _uiState.value = PurchaseDocumentViewerState.NotFound
            return
        }

        viewModelScope.launch {
            try {
                val receipt = purchaseRepository.getReceipt(receiptId)
                val path = receipt?.attachmentPath
                val displayName = receipt?.attachmentDisplayName
                if (path != null) {
                    val metadata = documentStore.inspect(path)?.let { m ->
                        if (displayName != null) m.copy(displayName = displayName) else m
                    }
                    if (metadata != null) {
                        var pageCount = 0
                        if (metadata.mimeType == "application/pdf") {
                            val file = PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, path)
                            val info = pdfRenderer.inspect(file)
                            pageCount = info.pageCount
                        }
                        _uiState.value = PurchaseDocumentViewerState.Ready(metadata, pageCount)
                    } else {
                        _uiState.value = PurchaseDocumentViewerState.NotFound
                    }
                } else {
                    _uiState.value = PurchaseDocumentViewerState.NotFound
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PurchaseDocumentViewerState.Error(e)
            }
        }
    }
}
