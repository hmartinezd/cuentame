package com.venkoi.restaurantops.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.venkoi.restaurantops.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RawOcrViewerUiState(
    val isLoading: Boolean = true,
    val result: PurchaseInvoiceOcrResult? = null,
    val pages: List<PurchaseInvoiceOcrPage> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RawOcrViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PurchaseRepository
) : ViewModel() {

    private val receiptId: String? = savedStateHandle["receiptId"]

    val uiState: StateFlow<RawOcrViewerUiState> = flow {
        if (receiptId == null) {
            emit(RawOcrViewerUiState(isLoading = false, error = "Missing receipt ID"))
            return@flow
        }

        repository.observeOcrResult(PurchaseReceiptId(receiptId)).collect { result ->
            if (result == null) {
                emit(RawOcrViewerUiState(isLoading = false, error = "No OCR evidence found"))
            } else {
                val pages = repository.getOcrPages(result.id)
                emit(RawOcrViewerUiState(isLoading = false, result = result, pages = pages))
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RawOcrViewerUiState()
    )
}
