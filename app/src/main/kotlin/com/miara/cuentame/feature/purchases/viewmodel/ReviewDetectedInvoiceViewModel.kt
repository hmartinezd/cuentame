package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewDetectedInvoiceUiState(
    val isLoading: Boolean = true,
    val result: PurchaseInvoiceParseResult? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class ReviewDetectedInvoiceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PurchaseRepository
) : ViewModel() {

    private val receiptId: PurchaseReceiptId = PurchaseReceiptId(
        checkNotNull(savedStateHandle.get<String>("receiptId"))
    )

    val uiState: StateFlow<ReviewDetectedInvoiceUiState> = repository.observeParseResult(receiptId)
        .map { result ->
            ReviewDetectedInvoiceUiState(
                isLoading = false,
                result = result
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReviewDetectedInvoiceUiState()
        )

    fun onUpdateHeader(updatedResult: PurchaseInvoiceParseResult) {
        viewModelScope.launch {
            repository.updateParseResult(receiptId, updatedResult)
        }
    }

    fun onUpdateLine(lineIndex: Int, isIgnored: Boolean, correction: ParsedInvoiceLineCandidate?) {
        viewModelScope.launch {
            repository.updateParsedLine(receiptId, lineIndex, isIgnored, correction)
        }
    }

    fun onToggleIgnoreLine(lineIndex: Int) {
        viewModelScope.launch {
            val line = uiState.value.result?.lines?.find { it.index == lineIndex } ?: return@launch
            repository.updateParsedLine(receiptId, lineIndex, !line.isIgnored, null)
        }
    }
}
