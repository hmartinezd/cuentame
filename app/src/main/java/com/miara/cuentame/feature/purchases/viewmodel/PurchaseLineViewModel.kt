package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.SavePurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class PurchaseLineUiState(
    val isSaving: Boolean = false,
    val lineId: PurchaseLineId? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val areas: List<InventoryArea> = emptyList(),
    val unitOptions: List<IngredientUnitOption> = emptyList(),
    val selectedIngredientId: IngredientId? = null,
    val selectedAreaId: InventoryAreaId? = null,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val quantityText: String = "",
    val totalText: String = "",
    val notes: String = "",
    val baseQuantityPreview: BigDecimal? = null,
    val unitCostPreview: BigDecimal? = null,
    val baseUnitSymbol: String = "",
    val error: Throwable? = null
)

sealed interface PurchaseLineEvent {
    data object Success : PurchaseLineEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseLineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savePurchaseLineUseCase: SavePurchaseLineUseCase,
    private val getPurchaseLineUseCase: com.miara.cuentame.core.domain.usecase.GetPurchaseLineUseCase,
    private val observeIngredientsUseCase: ObserveIngredientsUseCase,
    private val observeInventoryAreasUseCase: ObserveInventoryAreasUseCase,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val getIngredientDetailUseCase: GetIngredientDetailUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val unitRepository: com.miara.cuentame.core.domain.repository.UnitRepository
) : ViewModel() {

    private val receiptId = PurchaseReceiptId(requireNotNull(savedStateHandle.get<String>("purchaseId")))
    private val lineId = savedStateHandle.get<String>("lineId")?.let { PurchaseLineId(it) }

    private val _uiState = MutableStateFlow(PurchaseLineUiState(lineId = lineId))
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<PurchaseLineEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val ingredients = restaurantIdFlow.flatMapLatest { rid ->
        observeIngredientsUseCase(rid, includeArchived = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val areas = observeInventoryAreasUseCase(activeOnly = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(ingredients, areas) { ing, ar -> ing to ar }.collect { (ing, ar) ->
                _uiState.update { it.copy(ingredients = ing, areas = ar) }
            }
        }
        
        if (lineId != null) {
            viewModelScope.launch {
                val line = getPurchaseLineUseCase(receiptId, lineId)
                if (line != null) {
                    _uiState.update { 
                        it.copy(
                            selectedIngredientId = line.ingredientId,
                            selectedAreaId = line.areaId,
                            selectedUnitOptionId = line.ingredientUnitOptionId,
                            quantityText = line.quantityEntered.toPlainString(),
                            totalText = line.lineTotal.toPlainString(),
                            notes = line.notes ?: ""
                        )
                    }
                    onIngredientSelected(line.ingredientId, line.ingredientUnitOptionId)
                }
            }
        }
    }

    fun onIngredientSelected(ingredientId: IngredientId, initialUnitOptionId: IngredientUnitOptionId? = null) {
        _uiState.update { it.copy(selectedIngredientId = ingredientId, selectedUnitOptionId = initialUnitOptionId) }
        viewModelScope.launch {
            observeIngredientUnitOptionsUseCase(ingredientId, includeArchived = false).collect { options ->
                val activeOptions = options.filter { it.isActive }
                val defaultPurchase = initialUnitOptionId?.let { id -> activeOptions.find { it.id == id } }
                    ?: activeOptions.find { it.isDefaultPurchase } 
                    ?: activeOptions.find { it.isBase }
                
                val ingredient = getIngredientDetailUseCase(ingredientId)
                val baseUnit = ingredient?.let { unitRepository.getById(it.baseUnitId) }

                _uiState.update { 
                    it.copy(
                        unitOptions = activeOptions,
                        selectedUnitOptionId = defaultPurchase?.id,
                        baseUnitSymbol = baseUnit?.symbol ?: ""
                    )
                }
                updatePreviews()
            }
        }
    }

    fun onAreaSelected(areaId: InventoryAreaId) {
        _uiState.update { it.copy(selectedAreaId = areaId) }
    }

    fun onUnitOptionSelected(optionId: IngredientUnitOptionId) {
        _uiState.update { it.copy(selectedUnitOptionId = optionId) }
        updatePreviews()
    }

    fun onQuantityChanged(text: String) {
        _uiState.update { it.copy(quantityText = text) }
        updatePreviews()
    }

    fun onTotalChanged(text: String) {
        _uiState.update { it.copy(totalText = text) }
        updatePreviews()
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    private fun updatePreviews() {
        val state = _uiState.value
        val qty = DecimalParser.parse(state.quantityText) ?: BigDecimal.ZERO
        val total = DecimalParser.parse(state.totalText) ?: BigDecimal.ZERO
        val option = state.unitOptions.find { it.id == state.selectedUnitOptionId }

        if (option != null && qty > BigDecimal.ZERO) {
            val qtyBase = qty.multiply(option.factorToBase, MathContext.DECIMAL128)
            val costBase = total.divide(qtyBase, MathContext.DECIMAL128)
            _uiState.update { it.copy(baseQuantityPreview = qtyBase, unitCostPreview = costBase) }
        } else {
            _uiState.update { it.copy(baseQuantityPreview = null, unitCostPreview = null) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        val qty = DecimalParser.parse(state.quantityText) ?: return
        val total = DecimalParser.parse(state.totalText) ?: return
        val ingredientId = state.selectedIngredientId ?: return
        val areaId = state.selectedAreaId ?: return
        val optionId = state.selectedUnitOptionId ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                savePurchaseLineUseCase(
                    SavePurchaseLineCommand(
                        receiptId = receiptId,
                        lineId = lineId,
                        ingredientId = ingredientId,
                        areaId = areaId,
                        ingredientUnitOptionId = optionId,
                        quantityEntered = qty,
                        lineTotal = total,
                        notes = state.notes
                    )
                )
                _events.send(PurchaseLineEvent.Success)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
