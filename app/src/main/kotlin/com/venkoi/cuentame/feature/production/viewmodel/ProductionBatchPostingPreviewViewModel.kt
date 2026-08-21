package com.venkoi.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.ProductionBatchId
import com.venkoi.cuentame.core.domain.repository.ProductionBatchPostingPreview
import com.venkoi.cuentame.core.domain.repository.ProductionBatchRepository
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.model.inventory.ProductionBatch
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import com.venkoi.cuentame.feature.production.presentation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProductionBatchPreviewEvent {
    data class Posted(val batchId: ProductionBatchId) : ProductionBatchPreviewEvent
}

data class ProductionBatchPreviewUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val isPosting: Boolean = false,
    
    val batch: ProductionBatch? = null,
    val preview: ProductionBatchPostingPreview? = null,
    val currencyCode: String = "",
    val outputUnitLabel: String = "",
    
    val blockers: List<UiMessage> = emptyList(),
    val hasNegativeBalances: Boolean = false,
    val hasUnavailableCosts: Boolean = false,
    
    val inlineError: UiMessage? = null
)

@HiltViewModel
class ProductionBatchPostingPreviewViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val ingredientRepository: com.venkoi.cuentame.core.domain.repository.IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val batchId = savedStateHandle.get<String>("batchId")
        ?.takeIf { it.isNotBlank() }
        ?.let { ProductionBatchId(it) }

    private val _uiState = MutableStateFlow(ProductionBatchPreviewUiState())
    val uiState: StateFlow<ProductionBatchPreviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProductionBatchPreviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)

    init {
        loadPreview()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadPreview() {
        if (batchId == null) {
            _uiState.update { it.copy(screenState = ProductionBatchScreenState.InvalidRoute) }
            return
        }

        viewModelScope.launch {
            retryTrigger.collectLatest {
                _uiState.update { it.copy(screenState = ProductionBatchScreenState.Loading) }
                try {
                    val restaurant = restaurantRepository.getRestaurant()
                    if (restaurant == null) {
                        _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_no_restaurant))) }
                        return@collectLatest
                    }

                    val batch = productionBatchRepository.getBatch(batchId)
                    if (batch == null) {
                        _uiState.update { it.copy(screenState = ProductionBatchScreenState.BatchNotFound) }
                        return@collectLatest
                    }

                    val preview = productionBatchRepository.calculatePostingPreview(batchId)
                    val unitOptions = ingredientRepository.getUnitOptions(batch.outputIngredientId, includeArchived = true)
                    val outputUnit = unitOptions.find { it.id == batch.outputUnitOptionId }
                        ?: run {
                            _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                            return@collectLatest
                        }
                    
                    _uiState.update {
                        it.copy(
                            screenState = ProductionBatchScreenState.Ready,
                            batch = batch,
                            preview = preview,
                            currencyCode = restaurant.currencyCode,
                            outputUnitLabel = outputUnit.displayName,
                            blockers = preview.blockers.map { b -> b.toUserMessage() },
                            hasNegativeBalances = preview.components.any { c -> c.createsNegativeBalance },
                            hasUnavailableCosts = preview.components.any { c -> c.costUnavailable }
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                }
            }
        }
    }

    fun onPost() {
        val state = _uiState.value
        if (state.isPosting || batchId == null || state.preview == null || state.blockers.isNotEmpty()) return

        _uiState.update { 
            it.copy(
                isPosting = true, 
                inlineError = null
            ) 
        }
        viewModelScope.launch {
            try {
                productionBatchRepository.post(batchId)
                _events.send(ProductionBatchPreviewEvent.Posted(batchId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.venkoi.cuentame.core.domain.validation.ProductionBatchValidationException) {
                _uiState.update { it.copy(inlineError = e.failures.toUserMessage()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_generic)) }
            } finally {
                _uiState.update { it.copy(isPosting = false) }
            }
        }
    }

    fun clearInlineError() {
        _uiState.update {
            it.copy(inlineError = null)
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }
}
