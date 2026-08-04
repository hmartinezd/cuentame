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
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

sealed interface ProductionBatchComponentEvent {
    data object Saved : ProductionBatchComponentEvent
}

data class ProductionBatchComponentUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val isSaving: Boolean = false,
    
    val batch: ProductionBatch? = null,
    val component: ProductionBatchComponent? = null,
    val ingredientName: String = "",
    val availableAreas: List<InventoryArea> = emptyList(),
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    val recipeUnitLabel: String = "",
    
    // Form state
    val selectedAreaId: InventoryAreaId? = null,
    val sourceAreaDirty: Boolean = false,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val unitDirty: Boolean = false,
    val actualQuantity: String = "",
    val quantityDirty: Boolean = false,
    val notes: String = "",
    val notesDirty: Boolean = false,
    
    val hasManualOverride: Boolean = false,
    
    val quantityError: Boolean = false,
    val quantityErrorMessage: UiMessage? = null,
    val inlineError: UiMessage? = null
)

@HiltViewModel
class ProductionBatchComponentViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val ingredientRepository: IngredientRepository,
    private val inventoryAreaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val batchId = savedStateHandle.get<String>("batchId")
        ?.takeIf { it.isNotBlank() }
        ?.let { ProductionBatchId(it) }

    private val componentId = savedStateHandle.get<String>("componentId")
        ?.takeIf { it.isNotBlank() }
        ?.let { ProductionBatchComponentId(it) }

    private val _uiState = MutableStateFlow(ProductionBatchComponentUiState())
    val uiState: StateFlow<ProductionBatchComponentUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProductionBatchComponentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)
    private var isInitialized = false

    init {
        observeData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        if (batchId == null || componentId == null) {
            _uiState.update { it.copy(screenState = ProductionBatchScreenState.InvalidRoute) }
            return@observeData
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
                            _uiState.update { it.copy(screenState = ProductionBatchScreenState.ParentNotEditable) }
                            return@collectLatest
                        }

                        val component = batch.components.find { it.id == componentId }
                        if (component == null) {
                            _uiState.update { it.copy(screenState = ProductionBatchScreenState.ComponentNotFound) }
                            return@collectLatest
                        }

                        val ingredient = ingredientRepository.getById(component.componentIngredientId)
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }

                        val unitOptions = ingredientRepository.getUnitOptions(component.componentIngredientId, includeArchived = true)
                        val currentUnit = unitOptions.find { it.id == component.unitOptionId }
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }
                        
                        val recipeUnit = unitOptions.find { it.id == component.recipeUnitOptionIdSnapshot }
                            ?: run {
                                _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                                return@collectLatest
                            }

                        val activeAreas = inventoryAreaRepository.observeActiveAreas().first()
                        val sourceArea = component.sourceAreaId?.let { inventoryAreaRepository.getById(it) }
                        
                        if (component.sourceAreaId != null && sourceArea == null) {
                            _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                            return@collectLatest
                        }

                        val allAreas = (activeAreas + listOfNotNull(sourceArea)).distinctBy { it.id }

                        if (!isInitialized) {
                            _uiState.update { state ->
                                state.copy(
                                    screenState = ProductionBatchScreenState.Ready,
                                    batch = batch,
                                    component = component,
                                    ingredientName = ingredient.name,
                                    availableAreas = allAreas,
                                    availableUnitOptions = unitOptions,
                                    recipeUnitLabel = recipeUnit.displayName,
                                    
                                    selectedAreaId = component.sourceAreaId,
                                    selectedUnitOptionId = component.unitOptionId,
                                    actualQuantity = component.actualQuantityEntered.toPlainString(),
                                    notes = component.notes ?: "",
                                    hasManualOverride = component.hasManualQuantityOverride
                                )
                            }
                            isInitialized = true
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    screenState = ProductionBatchScreenState.Ready,
                                    batch = batch,
                                    component = component,
                                    ingredientName = ingredient.name,
                                    availableAreas = allAreas,
                                    availableUnitOptions = unitOptions,
                                    recipeUnitLabel = recipeUnit.displayName
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

    fun onAreaSelected(areaId: InventoryAreaId) {
        _uiState.update { it.copy(selectedAreaId = areaId, sourceAreaDirty = true) }
    }

    fun onUnitOptionSelected(optionId: IngredientUnitOptionId) {
        val state = _uiState.value
        val available = state.availableUnitOptions.find { it.id == optionId }
        
        if (available == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_unit_option_not_found)) }
            return
        }

        val oldOption = state.availableUnitOptions.find { it.id == state.selectedUnitOptionId }
        val newOption = available
        
        if (oldOption != null && oldOption.id != newOption.id) {
            val currentEntered = try { BigDecimal(state.actualQuantity) } catch (e: Exception) { BigDecimal.ZERO }
            val baseQty = currentEntered.multiply(oldOption.factorToBase, MathContext.DECIMAL128)
            val newEntered = baseQty.divide(newOption.factorToBase, MathContext.DECIMAL128)
            
            _uiState.update { it.copy(
                selectedUnitOptionId = optionId,
                actualQuantity = newEntered.stripTrailingZeros().toPlainString(),
                unitDirty = true,
                quantityDirty = true,
                hasManualOverride = true,
                inlineError = null
            ) }
        } else {
            _uiState.update { it.copy(selectedUnitOptionId = optionId, unitDirty = true, inlineError = null) }
        }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(
            actualQuantity = quantity,
            quantityDirty = true,
            quantityError = false,
            quantityErrorMessage = null,
            inlineError = null
        ) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes, notesDirty = true) }
    }

    fun onOverrideQuantity() {
        _uiState.update { it.copy(hasManualOverride = true, quantityDirty = true) }
    }

    fun onResetToRecipe() {
        if (batchId == null || componentId == null || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        viewModelScope.launch {
            try {
                productionBatchRepository.resetComponentToExpected(batchId, componentId)
                val updatedBatch = productionBatchRepository.getBatch(batchId)
                if (updatedBatch == null) {
                    _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_batch_not_found)) }
                    return@launch
                }
                val updatedComponent = updatedBatch.components.find { it.id == componentId }
                if (updatedComponent == null) {
                    _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_component_not_found)) }
                    return@launch
                }
                
                _uiState.update { it.copy(
                    component = updatedComponent,
                    actualQuantity = updatedComponent.actualQuantityEntered.toPlainString(),
                    selectedUnitOptionId = updatedComponent.unitOptionId,
                    hasManualOverride = updatedComponent.hasManualQuantityOverride,
                    quantityDirty = false,
                    unitDirty = false,
                    quantityError = false,
                    quantityErrorMessage = null,
                    inlineError = null
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

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || batchId == null || componentId == null) return

        val parsedQuantity = if (state.quantityDirty || state.unitDirty) {
            val trimmed = state.actualQuantity.trim()
            if (trimmed.isEmpty()) {
                _uiState.update { it.copy(quantityError = true, quantityErrorMessage = UiMessage.Resource(R.string.error_quantity_required)) }
                return
            }
            val parsed = trimmed.toBigDecimalOrNull()
            if (parsed == null) {
                _uiState.update { it.copy(quantityError = true, quantityErrorMessage = UiMessage.Resource(R.string.error_invalid_decimal)) }
                return
            }
            if (parsed <= BigDecimal.ZERO) {
                _uiState.update { it.copy(quantityError = true, quantityErrorMessage = UiMessage.Resource(R.string.error_quantity_positive)) }
                return
            }
            parsed
        } else null

        if (state.quantityDirty || state.unitDirty) {
            if (state.selectedUnitOptionId == null) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_unit_required)) }
                return
            }
            if (state.availableUnitOptions.none { it.id == state.selectedUnitOptionId }) {
                _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_unit_option_not_found)) }
                return
            }
        }

        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        viewModelScope.launch {
            try {
                productionBatchRepository.updateComponent(
                    UpdateProductionBatchComponentCommand(
                        batchId = batchId,
                        componentId = componentId,
                        sourceAreaId = state.selectedAreaId.takeIf { state.sourceAreaDirty },
                        actualQuantityEntered = parsedQuantity,
                        unitOptionId = state.selectedUnitOptionId.takeIf { state.quantityDirty || state.unitDirty },
                        notes = state.notes.takeIf { state.notesDirty }?.trim() ?: if (state.notesDirty) "" else null
                    )
                )
                _events.send(ProductionBatchComponentEvent.Saved)
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

    fun onRetry() {
        retryTrigger.value += 1
    }
}
