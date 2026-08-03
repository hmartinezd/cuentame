package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.feature.production.presentation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProductionBatchDetailEvent {
    data class NavigateToDraft(val batchId: ProductionBatchId) : ProductionBatchDetailEvent
}

data class ProductionBatchDetailUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val isOperating: Boolean = false,
    
    val batch: ProductionBatch? = null,
    val outputIngredientName: String = "",
    val outputAreaName: String = "",
    val outputUnitLabel: String = "",
    val currencyCode: String = "",
    
    val componentNames: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentUnitLabels: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentAreaNames: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentRecipeUnitLabels: Map<ProductionBatchComponentId, String> = emptyMap(),
    
    val inlineError: UiMessage? = null
)

@HiltViewModel
class ProductionBatchDetailViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val ingredientRepository: IngredientRepository,
    private val inventoryAreaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val batchId = savedStateHandle.get<String>("batchId")
        ?.takeIf { it.isNotBlank() }
        ?.let { ProductionBatchId(it) }

    private val _uiState = MutableStateFlow(ProductionBatchDetailUiState())
    val uiState: StateFlow<ProductionBatchDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProductionBatchDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)
    private var draftNavigationEmitted = false

    init {
        observeData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
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

                    productionBatchRepository.observeBatch(batchId).collectLatest { batch ->
                        if (batch == null) {
                            _uiState.update { it.copy(screenState = ProductionBatchScreenState.BatchNotFound) }
                            return@collectLatest
                        }

                        if (batch.status == DocumentStatus.DRAFT) {
                            if (!draftNavigationEmitted) {
                                draftNavigationEmitted = true
                                _events.send(ProductionBatchDetailEvent.NavigateToDraft(batch.id))
                            }
                            return@collectLatest
                        }

                        // Strict enrichment
                        val outputIng = ingredientRepository.getById(batch.outputIngredientId)
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }
                        val outputArea = inventoryAreaRepository.getById(batch.outputAreaId)
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }
                        val outputUnit = ingredientRepository.getUnitOptions(batch.outputIngredientId, true).find { it.id == batch.outputUnitOptionId }
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }

                        val componentNames = mutableMapOf<ProductionBatchComponentId, String>()
                        val componentUnitLabels = mutableMapOf<ProductionBatchComponentId, String>()
                        val componentAreaNames = mutableMapOf<ProductionBatchComponentId, String>()
                        val componentRecipeUnitLabels = mutableMapOf<ProductionBatchComponentId, String>()

                        for (comp in batch.components) {
                            val compIng = ingredientRepository.getById(comp.componentIngredientId)
                                ?: run {
                                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                    return@collectLatest
                                }
                            componentNames[comp.id] = compIng.name
                            
                            val compOptions = ingredientRepository.getUnitOptions(comp.componentIngredientId, true)
                            val compUnit = compOptions.find { it.id == comp.unitOptionId }
                                ?: run {
                                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                    return@collectLatest
                                }
                            componentUnitLabels[comp.id] = compUnit.displayName

                            val compRecipeUnit = compOptions.find { it.id == comp.recipeUnitOptionIdSnapshot }
                                ?: run {
                                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                    return@collectLatest
                                }
                            componentRecipeUnitLabels[comp.id] = compRecipeUnit.displayName

                            comp.sourceAreaId?.let { areaId ->
                                val area = inventoryAreaRepository.getById(areaId)
                                    ?: run {
                                        _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                        return@collectLatest
                                    }
                                componentAreaNames[comp.id] = area.name
                            }
                        }

                        _uiState.update {
                            it.copy(
                                screenState = ProductionBatchScreenState.Ready,
                                batch = batch,
                                outputIngredientName = outputIng.name,
                                outputAreaName = outputArea.name,
                                outputUnitLabel = outputUnit.displayName,
                                currencyCode = restaurant.currencyCode,
                                componentNames = componentNames,
                                componentUnitLabels = componentUnitLabels,
                                componentAreaNames = componentAreaNames,
                                componentRecipeUnitLabels = componentRecipeUnitLabels
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                }
            }
        }
    }

    fun onVoid() {
        if (batchId == null || _uiState.value.isOperating) return
        _uiState.update { it.copy(isOperating = true, inlineError = null) }
        viewModelScope.launch {
            try {
                productionBatchRepository.void(batchId)
                // Observation will handle status change
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProductionBatchValidationException) {
                _uiState.update { it.copy(inlineError = e.failures.toUserMessage(), isOperating = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_generic), isOperating = false) }
            } finally {
                _uiState.update { it.copy(isOperating = false) }
            }
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }

    fun clearInlineError() {
        _uiState.update { it.copy(inlineError = null) }
    }
}
