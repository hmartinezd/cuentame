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
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.domain.usecase.GetPurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.domain.usecase.PreviewPurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.SavePurchaseLineUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PurchaseLineScreenState {
    data object Loading : PurchaseLineScreenState
    data object Ready : PurchaseLineScreenState
    data object NotFound : PurchaseLineScreenState
    data object InvalidRoute : PurchaseLineScreenState
    data class Error(val throwable: Throwable) : PurchaseLineScreenState
}

data class PurchaseLineUiState(
    val screenState: PurchaseLineScreenState = PurchaseLineScreenState.Loading,
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
    val fieldErrors: Map<String, Int> = emptyMap(),
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
    private val getPurchaseLineUseCase: GetPurchaseLineUseCase,
    private val observeIngredientsUseCase: ObserveIngredientsUseCase,
    private val observeInventoryAreasUseCase: ObserveInventoryAreasUseCase,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val getIngredientDetailUseCase: GetIngredientDetailUseCase,
    private val previewPurchaseLineUseCase: PreviewPurchaseLineUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val unitRepository: UnitRepository
) : ViewModel() {

    private val purchaseIdStr: String? = savedStateHandle["purchaseId"]
    private val receiptId = purchaseIdStr?.let { PurchaseReceiptId(it) }
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

    private val _selectedIngredientId = MutableStateFlow<IngredientId?>(null)
    val unitOptions: StateFlow<List<IngredientUnitOption>> = _selectedIngredientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else observeIngredientUnitOptionsUseCase(id, includeArchived = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (receiptId == null) {
             _uiState.update { it.copy(screenState = PurchaseLineScreenState.InvalidRoute) }
        } else {
            loadInitialData()
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            if (lineId != null && receiptId != null) {
                try {
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
                        _selectedIngredientId.value = line.ingredientId
                        val ingredient = getIngredientDetailUseCase(line.ingredientId)
                        val baseUnit = ingredient?.let { unitRepository.getById(it.baseUnitId) }
                        _uiState.update { 
                            it.copy(
                                baseUnitSymbol = baseUnit?.symbol ?: "",
                                screenState = PurchaseLineScreenState.Ready
                            ) 
                        }
                    } else {
                        _uiState.update { it.copy(screenState = PurchaseLineScreenState.NotFound) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(screenState = PurchaseLineScreenState.Error(e)) }
                }
            } else {
                _uiState.update { it.copy(screenState = PurchaseLineScreenState.Ready) }
            }
        }

        viewModelScope.launch {
            combine(ingredients, areas, unitOptions) { ing, ar, opt -> Triple(ing, ar, opt) }.collect { (ing, ar, opt) ->
                _uiState.update { it.copy(ingredients = ing, areas = ar, unitOptions = opt) }
            }
        }
    }

    fun onIngredientSelected(ingredientId: IngredientId) {
        if (_selectedIngredientId.value == ingredientId) return
        _selectedIngredientId.value = ingredientId
        _uiState.update { it.copy(selectedIngredientId = ingredientId, selectedUnitOptionId = null) }
        viewModelScope.launch {
            val ingredient = getIngredientDetailUseCase(ingredientId)
            val baseUnit = ingredient?.let { unitRepository.getById(it.baseUnitId) }
            _uiState.update { it.copy(baseUnitSymbol = baseUnit?.symbol ?: "") }
            
            val options = observeIngredientUnitOptionsUseCase(ingredientId, includeArchived = false).first()
            val activeOptions = options.filter { it.isActive }
            val defaultPurchase = activeOptions.find { it.isDefaultPurchase } ?: activeOptions.find { it.isBase }
            _uiState.update { it.copy(selectedUnitOptionId = defaultPurchase?.id) }
            updatePreviews()
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

        if (option != null && qty > BigDecimal.ZERO && total >= BigDecimal.ZERO) {
            try {
                val calculation = previewPurchaseLineUseCase(qty, total, option.factorToBase)
                _uiState.update { it.copy(baseQuantityPreview = calculation.quantityBase, unitCostPreview = calculation.unitCostBase) }
            } catch (e: Exception) {
                _uiState.update { it.copy(baseQuantityPreview = null, unitCostPreview = null) }
            }
        } else {
            _uiState.update { it.copy(baseQuantityPreview = null, unitCostPreview = null) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || receiptId == null) return

        val errors = mutableMapOf<String, Int>()
        val qty = DecimalParser.parse(state.quantityText)
        val total = DecimalParser.parse(state.totalText)

        if (state.selectedIngredientId == null) errors["ingredient"] = com.miara.cuentame.R.string.error_ingredient_required
        if (state.selectedAreaId == null) errors["area"] = com.miara.cuentame.R.string.error_area_required
        if (state.selectedUnitOptionId == null) errors["unit"] = com.miara.cuentame.R.string.error_unit_required
        if (qty == null) errors["quantity"] = com.miara.cuentame.R.string.error_invalid_decimal
        else if (qty <= BigDecimal.ZERO) errors["quantity"] = com.miara.cuentame.R.string.error_quantity_positive
        
        if (total == null) errors["total"] = com.miara.cuentame.R.string.error_invalid_decimal
        else if (total < BigDecimal.ZERO) errors["total"] = com.miara.cuentame.R.string.error_total_negative

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            try {
                savePurchaseLineUseCase(
                    SavePurchaseLineCommand(
                        receiptId = receiptId,
                        lineId = lineId,
                        ingredientId = state.selectedIngredientId!!,
                        areaId = state.selectedAreaId!!,
                        ingredientUnitOptionId = state.selectedUnitOptionId!!,
                        quantityEntered = qty!!,
                        lineTotal = total!!,
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
