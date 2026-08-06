package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.StoredPurchaseDocument
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PurchaseDocumentViewerState {
    data object Loading : PurchaseDocumentViewerState
    data class Ready(val document: StoredPurchaseDocument) : PurchaseDocumentViewerState
    data object NotFound : PurchaseDocumentViewerState
    data class Error(val throwable: Throwable) : PurchaseDocumentViewerState
}

@HiltViewModel
class PurchaseDocumentViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseRepository: PurchaseRepository,
    private val documentStore: PurchaseDocumentStore
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
                        _uiState.value = PurchaseDocumentViewerState.Ready(metadata)
                    } else {
                        _uiState.value = PurchaseDocumentViewerState.NotFound
                    }
                } else {
                    _uiState.value = PurchaseDocumentViewerState.NotFound
                }
            } catch (e: Exception) {
                _uiState.value = PurchaseDocumentViewerState.Error(e)
            }
        }
    }
}
