package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.StockCountDetails
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.usecase.PreviewStockCountLineUseCase
import com.miara.cuentame.core.domain.usecase.StockCountLinePreview
import com.miara.cuentame.core.model.inventory.StockCountStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class StockCountReviewLine(
    val ingredientId: String,
    val ingredientName: String,
    val areaName: String,
    val quantityEntered: BigDecimal,
    val unitName: String,
    val quantityBase: BigDecimal,
    val preview: StockCountLinePreview
)

data class StockCountDetailUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val isInvalidRoute: Boolean = false,
    val isDeleting: Boolean = false,
    val isCompleting: Boolean = false,
    val isVoiding: Boolean = false,
    val details: StockCountDetails? = null,
    val currencyCode: String = "USD",
    val reviewLines: List<StockCountReviewLine> = emptyList(),
    val showReview: Boolean = false,
    val error: Throwable? = null
)

sealed interface StockCountDetailEvent {
    data object Deleted : StockCountDetailEvent
    data object Completed : StockCountDetailEvent
    data object Voided : StockCountDetailEvent
}

@HiltViewModel
class StockCountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockCountRepository,
    private val ingredientRepository: IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    private val previewUseCase: PreviewStockCountLineUseCase
) : ViewModel() {

    private val countIdStr: String? = savedStateHandle["countId"]
    private val countId = countIdStr?.let { StockCountId(it) }

    private val _isDeleting = MutableStateFlow(false)
    private val _isCompleting = MutableStateFlow(false)
    private val _isVoiding = MutableStateFlow(false)
    private val _showReview = MutableStateFlow(false)
    private val _reviewLines = MutableStateFlow<List<StockCountReviewLine>>(emptyList())
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<StockCountDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<StockCountDetailUiState> = combine(
        if (countId != null) repository.observeCount(countId) else kotlinx.coroutines.flow.flowOf(null),
        restaurantRepository.observeRestaurant().map { it?.currencyCode ?: "USD" },
        _isDeleting,
        _isCompleting,
        _isVoiding,
        _showReview,
        _reviewLines,
        _error
    ) { args ->
        val details = args[0] as StockCountDetails?
        val currencyCode = args[1] as String
        val deleting = args[2] as Boolean
        val completing = args[3] as Boolean
        val voiding = args[4] as Boolean
        val showReview = args[5] as Boolean
        val reviewLines = args[6] as List<StockCountReviewLine>
        val error = args[7] as Throwable?

        StockCountDetailUiState(
            isLoading = details == null && error == null && countId != null && !deleting,
            isNotFound = details == null && countId != null && !deleting && error == null,
            isInvalidRoute = countId == null,
            isDeleting = deleting,
            isCompleting = completing,
            isVoiding = voiding,
            details = details,
            currencyCode = currencyCode,
            reviewLines = reviewLines,
            showReview = showReview,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockCountDetailUiState()
    )

    fun onDelete() {
        val cid = countId ?: return
        if (_isDeleting.value) return
        _isDeleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.deleteDraft(cid)
                _events.send(StockCountDetailEvent.Deleted)
            } catch (e: Exception) {
                _isDeleting.update { false }
                _error.value = e
            }
        }
    }

    fun onToggleReview(show: Boolean) {
        if (show && _reviewLines.value.isEmpty()) {
             generateReviewData()
        }
        _showReview.value = show
    }

    private fun generateReviewData() {
        val details = uiState.value.details ?: return
        viewModelScope.launch {
            val lines = mutableListOf<StockCountReviewLine>()
            details.areas.forEach { areaDetail ->
                areaDetail.lines.forEach { line ->
                    val ingredient = ingredientRepository.getById(line.ingredientId) ?: return@forEach
                    val options = ingredientRepository.getUnitOptions(line.ingredientId)
                    val option = options.find { it.id == line.ingredientUnitOptionId } ?: return@forEach
                    
                    val preview = previewUseCase(
                        restaurantId = areaDetail.restaurantId,
                        ingredientId = line.ingredientId,
                        areaId = areaDetail.area.areaId,
                        effectiveAt = areaDetail.effectiveAt,
                        quantityBase = line.quantityBase
                    )

                    lines.add(
                        StockCountReviewLine(
                            ingredientId = line.ingredientId.value,
                            ingredientName = ingredient.name,
                            areaName = areaDetail.areaName,
                            quantityEntered = line.quantityEntered,
                            unitName = option.shortLabel,
                            quantityBase = line.quantityBase,
                            preview = preview
                        )
                    )
                }
            }
            _reviewLines.value = lines
        }
    }

    fun onComplete() {
        val cid = countId ?: return
        if (_isCompleting.value) return
        _isCompleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.completeCount(cid)
                _events.send(StockCountDetailEvent.Completed)
                _isCompleting.value = false
                _showReview.value = false
            } catch (e: Exception) {
                _isCompleting.value = false
                _error.value = e
            }
        }
    }

    fun onVoid() {
        val cid = countId ?: return
        if (_isVoiding.value) return
        _isVoiding.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.voidCount(cid)
                _events.send(StockCountDetailEvent.Voided)
                _isVoiding.value = false
            } catch (e: Exception) {
                _isVoiding.value = false
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
