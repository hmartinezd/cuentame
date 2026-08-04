package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.feature.production.presentation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

sealed interface ProductionBatchDraftEvent {
    data class NavigateToDetail(val batchId: ProductionBatchId) : ProductionBatchDraftEvent
    data class NavigateToPreview(val batchId: ProductionBatchId) : ProductionBatchDraftEvent
    data object Deleted : ProductionBatchDraftEvent
}

data class ProductionBatchDraftUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    
    val batch: ProductionBatch? = null,
    val outputIngredientName: String = "",
    val availableAreas: List<InventoryArea> = emptyList(),
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    val componentNames: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentUnitLabels: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentRecipeUnitLabels: Map<ProductionBatchComponentId, String> = emptyMap(),
    val componentAreaNames: Map<ProductionBatchComponentId, String> = emptyMap(),
    
    // Form state
    val multiplier: String = "",
    val multiplierDirty: Boolean = false,
    val selectedAreaId: InventoryAreaId? = null,
    val outputAreaDirty: Boolean = false,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val outputUnitDirty: Boolean = false,
    val actualOutputQuantity: String = "",
    val outputQuantityDirty: Boolean = false,
    val effectiveAt: Instant = Instant.now(),
    val effectiveAtDirty: Boolean = false,
    val notes: String = "",
    val notesDirty: Boolean = false,
    
    val hasManualOutputOverride: Boolean = false,
    
    // Calculated
    val expectedOutputEntered: BigDecimal? = null,
    
    // Validation
    val multiplierError: Boolean = false,
    val actualOutputError: Boolean = false,
    val inlineError: UiMessage? = null
) {
    val hasUnsavedChanges: Boolean
        get() = multiplierDirty ||
                outputAreaDirty ||
                outputUnitDirty ||
                outputQuantityDirty ||
                effectiveAtDirty ||
                notesDirty
}

@HiltViewModel
class ProductionBatchDraftViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val ingredientRepository: IngredientRepository,
    private val inventoryAreaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val batchId = savedStateHandle.get<String>("batchId")
        ?.takeIf { it.isNotBlank() }
        ?.let { ProductionBatchId(it) }

    private val _uiState = MutableStateFlow(ProductionBatchDraftUiState())
    val uiState: StateFlow<ProductionBatchDraftUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProductionBatchDraftEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)
    private var isInitialized = false
    private var nonDraftNavigationEmitted = false

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

                        if (batch.status != DocumentStatus.DRAFT) {
                            if (!nonDraftNavigationEmitted) {
                                nonDraftNavigationEmitted = true
                                _events.send(ProductionBatchDraftEvent.NavigateToDetail(batch.id))
                            }
                            return@collectLatest
                        }

                        val activeAreas = inventoryAreaRepository.observeActiveAreas().first()
                        val currentArea = batch.outputAreaId.let { inventoryAreaRepository.getById(it) }
                        
                        // Strict output area enrichment
                        if (currentArea == null) {
                             _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                             return@collectLatest
                        }

                        val allAreas = (activeAreas + listOf(currentArea)).distinctBy { it.id }

                        val outputIngredient = ingredientRepository.getById(batch.outputIngredientId)
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }

                        val unitOptions = ingredientRepository.getUnitOptions(batch.outputIngredientId, includeArchived = true)
                        val outputUnit = unitOptions.find { it.id == batch.outputUnitOptionId }
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

                        if (!isInitialized) {
                            _uiState.update { state ->
                                state.copy(
                                    screenState = ProductionBatchScreenState.Ready,
                                    batch = batch,
                                    outputIngredientName = outputIngredient.name,
                                    availableAreas = allAreas,
                                    availableUnitOptions = unitOptions,
                                    componentNames = componentNames,
                                    componentUnitLabels = componentUnitLabels,
                                    componentRecipeUnitLabels = componentRecipeUnitLabels,
                                    componentAreaNames = componentAreaNames,
                                    
                                    multiplier = batch.batchMultiplier.toPlainString(),
                                    selectedAreaId = batch.outputAreaId,
                                    selectedUnitOptionId = batch.outputUnitOptionId,
                                    actualOutputQuantity = batch.actualOutputQuantityEntered.toPlainString(),
                                    effectiveAt = batch.effectiveAt,
                                    notes = batch.notes ?: "",
                                    hasManualOutputOverride = batch.hasManualOutputQuantityOverride
                                )
                            }
                            calculateExpectedOutput()
                            isInitialized = true
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    screenState = ProductionBatchScreenState.Ready,
                                    batch = batch,
                                    outputIngredientName = outputIngredient.name,
                                    availableAreas = allAreas,
                                    availableUnitOptions = unitOptions,
                                    componentNames = componentNames,
                                    componentUnitLabels = componentUnitLabels,
                                    componentRecipeUnitLabels = componentRecipeUnitLabels,
                                    componentAreaNames = componentAreaNames
                                )
                            }
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

    fun onMultiplierChanged(multiplier: String) {
        _uiState.update { it.copy(multiplier = multiplier, multiplierDirty = true, multiplierError = false, inlineError = null) }
        calculateExpectedOutput()
    }

    fun onAreaSelected(areaId: InventoryAreaId) {
        _uiState.update { it.copy(selectedAreaId = areaId, outputAreaDirty = true, inlineError = null) }
    }

    fun onUnitOptionSelected(optionId: IngredientUnitOptionId) {
        val state = _uiState.value
        val batch = state.batch ?: return
        val oldOption = state.availableUnitOptions.find { it.id == state.selectedUnitOptionId }
        val newOption = state.availableUnitOptions.find { it.id == optionId }
        
        if (oldOption != null && newOption != null && oldOption.id != newOption.id) {
            // Preserve base quantity
            val currentEntered = try { BigDecimal(state.actualOutputQuantity) } catch (e: Exception) { BigDecimal.ZERO }
            val baseQty = currentEntered.multiply(oldOption.factorToBase, MathContext.DECIMAL128)
            val newEntered = baseQty.divide(newOption.factorToBase, MathContext.DECIMAL128)
            
            _uiState.update { it.copy(
                selectedUnitOptionId = optionId,
                actualOutputQuantity = newEntered.stripTrailingZeros().toPlainString(),
                outputUnitDirty = true,
                outputQuantityDirty = true,
                hasManualOutputOverride = true,
                inlineError = null
            ) }
        } else {
            _uiState.update { it.copy(selectedUnitOptionId = optionId, outputUnitDirty = true, inlineError = null) }
        }
    }

    fun onActualOutputChanged(quantity: String) {
        _uiState.update { it.copy(actualOutputQuantity = quantity, outputQuantityDirty = true, actualOutputError = false, inlineError = null) }
    }

    fun onEffectiveAtChanged(instant: Instant) {
        _uiState.update { it.copy(effectiveAt = instant, effectiveAtDirty = true, inlineError = null) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes, notesDirty = true, inlineError = null) }
    }

    fun onOverrideOutput() {
        _uiState.update { it.copy(hasManualOutputOverride = true, outputQuantityDirty = true) }
    }

    private fun calculateExpectedOutput() {
        val state = _uiState.value
        val batch = state.batch ?: return
        val yield = batch.recipeStandardYieldQuantitySnapshot
        val multiplierVal = try {
            BigDecimal(state.multiplier)
        } catch (e: Exception) {
            null
        }

        if (multiplierVal != null && multiplierVal > BigDecimal.ZERO) {
            val expected = yield.multiply(multiplierVal, MathContext.DECIMAL128)
            _uiState.update { it.copy(expectedOutputEntered = expected) }
        } else {
            _uiState.update { it.copy(expectedOutputEntered = null) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isDeleting || batchId == null) return

        val multiplierVal = if (state.multiplierDirty) {
            try { BigDecimal(state.multiplier) } catch (e: Exception) {
                _uiState.update { it.copy(multiplierError = true, inlineError = UiMessage.Resource(R.string.error_invalid_decimal)) }
                return
            }
        } else null

        if (multiplierVal != null && multiplierVal <= BigDecimal.ZERO) {
            _uiState.update { it.copy(multiplierError = true, inlineError = UiMessage.Resource(R.string.error_multiplier_positive)) }
            return
        }

        val actualOutputVal = if (state.outputQuantityDirty || state.outputUnitDirty) {
            try { BigDecimal(state.actualOutputQuantity) } catch (e: Exception) {
                _uiState.update { it.copy(actualOutputError = true, inlineError = UiMessage.Resource(R.string.error_invalid_decimal)) }
                return
            }
        } else null

        if (actualOutputVal != null && actualOutputVal <= BigDecimal.ZERO) {
            _uiState.update { it.copy(actualOutputError = true, inlineError = UiMessage.Resource(R.string.error_quantity_positive)) }
            return
        }

        if (state.effectiveAtDirty && state.effectiveAt.isAfter(timeProvider.now())) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_future_effective_time)) }
            return
        }

        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        viewModelScope.launch {
            try {
                productionBatchRepository.updateDraft(
                    UpdateProductionBatchDraftCommand(
                        batchId = batchId,
                        batchMultiplier = multiplierVal,
                        outputAreaId = state.selectedAreaId.takeIf { state.outputAreaDirty },
                        actualOutputQuantityEntered = actualOutputVal,
                        outputUnitOptionId = state.selectedUnitOptionId.takeIf { state.outputQuantityDirty || state.outputUnitDirty },
                        effectiveAt = state.effectiveAt.takeIf { state.effectiveAtDirty },
                        notes = state.notes.takeIf { state.notesDirty }?.trim() ?: if (state.notesDirty) "" else null
                    )
                )
                _uiState.update { it.copy(
                    multiplierDirty = false,
                    outputAreaDirty = false,
                    outputUnitDirty = false,
                    outputQuantityDirty = false,
                    effectiveAtDirty = false,
                    notesDirty = false
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProductionBatchValidationException) {
                _uiState.update { it.copy(inlineError = e.failures.toUserMessage()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_generic)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onDelete() {
        val state = _uiState.value
        if (batchId == null || state.isDeleting || state.isSaving) return
        _uiState.update { it.copy(isDeleting = true, inlineError = null) }
        viewModelScope.launch {
            try {
                productionBatchRepository.deleteDraft(batchId)
                _events.send(ProductionBatchDraftEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProductionBatchValidationException) {
                _uiState.update { it.copy(inlineError = e.failures.toUserMessage()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_generic)) }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun onReview() {
        val state = _uiState.value
        if (state.hasUnsavedChanges || state.isSaving || state.isDeleting || batchId == null) return
        viewModelScope.launch {
            _events.send(ProductionBatchDraftEvent.NavigateToPreview(batchId))
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }
}
