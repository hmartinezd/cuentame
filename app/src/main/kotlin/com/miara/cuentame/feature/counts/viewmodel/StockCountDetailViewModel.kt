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
import kotlinx.coroutines.flow.onEach
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
    val baseUnitName: String,
    val preview: StockCountLinePreview
)

sealed interface StockCountDetailScreenState {
    data object Loading : StockCountDetailScreenState
    data object Ready : StockCountDetailScreenState
    data object NotFound : StockCountDetailScreenState
    data object InvalidRoute : StockCountDetailScreenState
    data class Error(val throwable: Throwable) : StockCountDetailScreenState
}

data class StockCountDetailUiState(
    val screenState: StockCountDetailScreenState = StockCountDetailScreenState.Loading,
    val isDeleting: Boolean = false,
    val isCompleting: Boolean = false,
    val isVoiding: Boolean = false,
    val details: StockCountDetails? = null,
    val currencyCode: String = "USD",
    val reviewLines: List<StockCountReviewLine> = emptyList(),
    val showReview: Boolean = false,
    val isReviewLoading: Boolean = false,
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
    private val _isReviewLoading = MutableStateFlow(false)
    private val _reviewLines = MutableStateFlow<List<StockCountReviewLine>>(emptyList())
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _events = Channel<StockCountDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (countId != null) {
            viewModelScope.launch {
                repository.observeCount(countId)
                    .onEach { _hasLoadedOnce.value = true }
                    .collect {
                        // Invalidate review data when count changes
                        _reviewLines.value = emptyList()
                    }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<StockCountDetailUiState> = combine(
        if (countId != null) repository.observeCount(countId) else kotlinx.coroutines.flow.flowOf(null),
        restaurantRepository.observeRestaurant().map { it?.currencyCode ?: "USD" },
        _isDeleting,
        _isCompleting,
        _isVoiding,
        _showReview,
        _isReviewLoading,
        _reviewLines,
        _error,
        _hasLoadedOnce
    ) { args ->
        val details = args[0] as StockCountDetails?
        val currencyCode = args[1] as String
        val deleting = args[2] as Boolean
        val completing = args[3] as Boolean
        val voiding = args[4] as Boolean
        val showReview = args[5] as Boolean
        val isReviewLoading = args[6] as Boolean
        val reviewLines = args[7] as List<StockCountReviewLine>
        val error = args[8] as Throwable?
        val hasLoadedOnce = args[9] as Boolean

        val screenState = when {
            countId == null || countIdStr.isNullOrBlank() -> StockCountDetailScreenState.InvalidRoute
            !hasLoadedOnce && error == null -> StockCountDetailScreenState.Loading
            error != null && details == null -> StockCountDetailScreenState.Error(error)
            details == null -> StockCountDetailScreenState.NotFound
            else -> StockCountDetailScreenState.Ready
        }

        StockCountDetailUiState(
            screenState = screenState,
            isDeleting = deleting,
            isCompleting = completing,
            isVoiding = voiding,
            details = details,
            currencyCode = currencyCode,
            reviewLines = reviewLines,
            showReview = showReview,
            isReviewLoading = isReviewLoading,
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
                _isDeleting.value = false
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
        _isReviewLoading.value = true
        viewModelScope.launch {
            try {
                val lines = mutableListOf<StockCountReviewLine>()
                details.areas.forEach { areaDetail ->
                    areaDetail.lines.forEach { line ->
                        val ingredient = ingredientRepository.getById(line.ingredientId) ?: return@forEach
                        val options = ingredientRepository.getUnitOptions(line.ingredientId, true)
                        val option = options.find { it.id == line.ingredientUnitOptionId } ?: return@forEach
                        val baseUnit = options.find { it.isBase }
                        
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
                                baseUnitName = baseUnit?.shortLabel ?: "units",
                                preview = preview
                            )
                        )
                    }
                }
                _reviewLines.value = lines
                _isReviewLoading.value = false
            } catch (e: Exception) {
                _isReviewLoading.value = false
                _error.value = e
                _showReview.value = false
            }
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
