package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.usecase.CreateIngredientUseCase
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.PreviewUnitConversionUseCase
import com.miara.cuentame.core.domain.usecase.UpdateIngredientUseCase
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.feature.ingredients.model.EditableUnitOptionUiModel
import com.miara.cuentame.feature.ingredients.model.IngredientFormUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface IngredientFormEvent {
    data class Created(val ingredientId: IngredientId) : IngredientFormEvent
    data class Updated(val ingredientId: IngredientId) : IngredientFormEvent
}

@HiltViewModel
class IngredientFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getIngredientDetailUseCase: GetIngredientDetailUseCase,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val observeIngredientCategoriesUseCase: ObserveIngredientCategoriesUseCase,
    private val observeCompatibleSystemUnitsUseCase: ObserveCompatibleSystemUnitsUseCase,
    private val createIngredientUseCase: CreateIngredientUseCase,
    private val updateIngredientUseCase: UpdateIngredientUseCase,
    private val previewUnitConversionUseCase: PreviewUnitConversionUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val unitRepository: UnitRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val ingredientId: String? = savedStateHandle["ingredientId"]
    
    private val _uiState = MutableStateFlow(IngredientFormUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<IngredientFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val categories = observeIngredientCategoriesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val compatibleUnits: StateFlow<List<UnitOfMeasure>> = _uiState
        .combine(MutableStateFlow<List<UnitOfMeasure>>(emptyList())) { state, _ ->
            state.selectedDimension?.let { dim ->
                observeCompatibleSystemUnitsUseCase(dim).first()
            } ?: emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            if (ingredientId != null) {
                val id = IngredientId(ingredientId)
                val ingredient = getIngredientDetailUseCase(id)
                if (ingredient != null) {
                    val options = observeIngredientUnitOptionsUseCase(ingredient.id).first()
                    val baseUnit = unitRepository.getById(ingredient.baseUnitId)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            ingredientId = ingredient.id,
                            name = ingredient.name,
                            selectedCategoryId = ingredient.categoryId,
                            selectedBaseUnitId = ingredient.baseUnitId,
                            selectedDimension = baseUnit?.dimension,
                            unitOptions = options.map { opt ->
                                EditableUnitOptionUiModel(
                                    id = opt.id.value,
                                    name = opt.displayName,
                                    standardUnitId = opt.standardUnitId,
                                    factorToBase = opt.factorToBase.toPlainString(),
                                    isBase = opt.isBase,
                                    isDefaultCount = opt.isDefaultCount,
                                    isDefaultPurchase = opt.isDefaultPurchase
                                )
                            }
                        )
                    }
                } else {
                    // Handle not found
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, fieldErrors = it.fieldErrors - "name") }
    }

    fun onCategorySelected(categoryId: IngredientCategoryId?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onDimensionSelected(dimension: UnitDimension) {
        if (_uiState.value.selectedDimension == dimension) return
        
        _uiState.update { 
            it.copy(
                selectedDimension = dimension,
                selectedBaseUnitId = null,
                unitOptions = emptyList()
            )
        }
    }

    fun onBaseUnitSelected(unit: UnitOfMeasure) {
        _uiState.update { 
            it.copy(
                selectedBaseUnitId = unit.id,
                unitOptions = listOf(
                    EditableUnitOptionUiModel(
                        id = idGenerator.newId(),
                        name = unit.symbol,
                        standardUnitId = unit.id,
                        factorToBase = "1",
                        isBase = true,
                        isDefaultCount = true,
                        isDefaultPurchase = true
                    )
                )
            )
        }
    }

    fun onAddStandardOption(unit: UnitOfMeasure) {
        val state = _uiState.value
        val baseUnitId = state.selectedBaseUnitId ?: return
        
        viewModelScope.launch {
            val baseUnit = unitRepository.getById(baseUnitId) ?: return@launch
            val factor = previewUnitConversionUseCase.preview(BigDecimal.ONE, unit, baseUnit)
            
            val newOption = EditableUnitOptionUiModel(
                id = idGenerator.newId(),
                name = unit.symbol,
                standardUnitId = unit.id,
                factorToBase = factor.toPlainString(),
                isBase = false
            )
            _uiState.update { it.copy(unitOptions = it.unitOptions + newOption) }
        }
    }

    fun onAddPackageOption(name: String, quantity: BigDecimal) {
        val newOption = EditableUnitOptionUiModel(
            id = idGenerator.newId(),
            name = name,
            factorToBase = quantity.toPlainString(),
            isBase = false
        )
        _uiState.update { it.copy(unitOptions = it.unitOptions + newOption) }
    }

    fun onRemoveOption(id: String) {
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.filter { it.id != id || it.isBase })
        }
    }

    fun onSetDefaultCount(id: String) {
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.map { it.copy(isDefaultCount = it.id == id) })
        }
    }

    fun onSetDefaultPurchase(id: String) {
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.map { it.copy(isDefaultPurchase = it.id == id) })
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSubmitting) return
        
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        
        viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant() ?: throw IllegalStateException("No restaurant")
                
                if (state.ingredientId == null) {
                    val newId = createIngredient(restaurant.id, state)
                    _events.send(IngredientFormEvent.Created(newId))
                } else {
                    updateIngredient(state)
                    _events.send(IngredientFormEvent.Updated(state.ingredientId))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e) }
            }
        }
    }

    private suspend fun createIngredient(restaurantId: com.miara.cuentame.core.common.ids.RestaurantId, state: IngredientFormUiState): IngredientId {
        val ingredientId = IngredientId(idGenerator.newId())
        val now = timeProvider.now()
        
        val ingredient = Ingredient(
            id = ingredientId,
            restaurantId = restaurantId,
            name = state.name,
            normalizedName = "",
            categoryId = state.selectedCategoryId,
            baseUnitId = state.selectedBaseUnitId!!,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        
        val baseOptionUi = state.unitOptions.first { it.isBase }
        val baseOption = IngredientUnitOption(
            id = IngredientUnitOptionId(baseOptionUi.id),
            ingredientId = ingredientId,
            displayName = baseOptionUi.name,
            shortLabel = baseOptionUi.name,
            standardUnitId = baseOptionUi.standardUnitId,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = baseOptionUi.isDefaultCount,
            isDefaultPurchase = baseOptionUi.isDefaultPurchase,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        
        val additionalOptions = state.unitOptions.filter { !it.isBase }.map { optUi ->
            IngredientUnitOption(
                id = IngredientUnitOptionId(optUi.id),
                ingredientId = ingredientId,
                displayName = optUi.name,
                shortLabel = optUi.name,
                standardUnitId = optUi.standardUnitId,
                factorToBase = BigDecimal(optUi.factorToBase),
                isBase = false,
                isDefaultCount = optUi.isDefaultCount,
                isDefaultPurchase = optUi.isDefaultPurchase,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        }
        
        createIngredientUseCase(ingredient, baseOption, additionalOptions)
        return ingredientId
    }

    private suspend fun updateIngredient(state: IngredientFormUiState) {
        val ingredientId = state.ingredientId!!
        val existing = getIngredientDetailUseCase(ingredientId) ?: throw ValidationError.IngredientNotFound
        
        val updated = existing.copy(
            name = state.name,
            categoryId = state.selectedCategoryId,
            updatedAt = timeProvider.now()
        )
        updateIngredientUseCase(updated)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
