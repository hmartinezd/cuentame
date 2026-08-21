package com.venkoi.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.IdGenerator
import com.venkoi.cuentame.core.common.ids.IngredientCategoryId
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.UnitId
import com.venkoi.cuentame.core.common.text.normalizeName
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository
import com.venkoi.cuentame.core.domain.repository.UnitRepository
import com.venkoi.cuentame.core.domain.usecase.CreateIngredientUseCase
import com.venkoi.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.venkoi.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.venkoi.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.venkoi.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.venkoi.cuentame.core.domain.usecase.PreviewUnitConversionUseCase
import com.venkoi.cuentame.core.domain.usecase.UpdateIngredientUseCase
import com.venkoi.cuentame.core.domain.repository.UpdateIngredientCommand
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.inventory.UnitDimension
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import com.venkoi.cuentame.feature.ingredients.model.EditableUnitOptionUiModel
import com.venkoi.cuentame.feature.ingredients.model.IngredientFormUiState
import com.venkoi.cuentame.feature.ingredients.model.UnitConversionChoiceUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val timeProvider: TimeProvider,
    private val inventoryAreaRepository: InventoryAreaRepository = EmptyIngredientAreaRepository
) : ViewModel() {

    private val ingredientId: String? = savedStateHandle["ingredientId"]
    private val prefillName: String? = savedStateHandle["prefillName"]
    
    private val _uiState = MutableStateFlow(IngredientFormUiState(
        ingredientId = ingredientId?.let { IngredientId(it) },
        name = prefillName ?: ""
    ))
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<IngredientFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val categories = observeIngredientCategoriesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas = inventoryAreaRepository.observeActiveAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val compatibleUnits: StateFlow<List<UnitOfMeasure>> = _uiState
        .flatMapLatest { state ->
            state.selectedDimension?.let { dim ->
                observeCompatibleSystemUnitsUseCase(dim)
            } ?: flowOf(emptyList())
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
                            parLevel = ingredient.parLevelBase?.stripTrailingZeros()?.toPlainString().orEmpty(),
                            reorderPoint = ingredient.reorderPointBase?.stripTrailingZeros()?.toPlainString().orEmpty(),
                            selectedCategoryId = ingredient.categoryId,
                            selectedDefaultAreaId = ingredient.defaultAreaId,
                            selectedBaseUnitId = ingredient.baseUnitId,
                            selectedDimension = baseUnit?.dimension,
                            unitOptions = options.map { opt ->
                                EditableUnitOptionUiModel(
                                    id = opt.id.value,
                                    name = opt.displayName,
                                    standardUnitId = opt.standardUnitId,
                                    factorToBase = opt.factorToBase.stripTrailingZeros().toPlainString(),
                                    isBase = opt.isBase,
                                    isDefaultCount = opt.isDefaultCount,
                                    isDefaultPurchase = opt.isDefaultPurchase
                                )
                            }
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, fieldErrors = it.fieldErrors - "name") }
    }

    fun onParLevelChanged(value: String) = _uiState.update { it.copy(parLevel = value, fieldErrors = it.fieldErrors - "par") }
    fun onReorderPointChanged(value: String) = _uiState.update { it.copy(reorderPoint = value, fieldErrors = it.fieldErrors - "reorderPoint") }

    fun onCategorySelected(categoryId: IngredientCategoryId?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onDefaultAreaSelected(areaId: com.venkoi.cuentame.core.common.ids.InventoryAreaId?) {
        _uiState.update { it.copy(selectedDefaultAreaId = areaId) }
    }

    fun onDimensionSelected(dimension: UnitDimension) {
        if (_uiState.value.isEditMode) return
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
        if (_uiState.value.isEditMode) return
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
        if (_uiState.value.isEditMode) return
        val state = _uiState.value
        val baseUnitId = state.selectedBaseUnitId ?: return
        
        viewModelScope.launch {
            val baseUnit = unitRepository.getById(baseUnitId) ?: return@launch
            val factor = previewUnitConversionUseCase.preview(BigDecimal.ONE, unit, baseUnit)
            
            val newOption = EditableUnitOptionUiModel(
                id = idGenerator.newId(),
                name = unit.symbol,
                standardUnitId = unit.id,
                factorToBase = factor.stripTrailingZeros().toPlainString(),
                isBase = false
            )
            _uiState.update { it.copy(unitOptions = it.unitOptions + newOption) }
        }
    }

    fun onAddPackageOption(name: String, quantity: BigDecimal) {
        if (_uiState.value.isEditMode) return
        val newOption = EditableUnitOptionUiModel(
            id = idGenerator.newId(),
            name = name,
            factorToBase = quantity.stripTrailingZeros().toPlainString(),
            isBase = false
        )
        _uiState.update { it.copy(unitOptions = it.unitOptions + newOption) }
    }

    fun onRemoveOption(id: String) {
        if (_uiState.value.isEditMode) return
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.filter { it.id != id || it.isBase })
        }
    }

    fun onSetDefaultCount(id: String) {
        if (_uiState.value.isEditMode) return
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.map { it.copy(isDefaultCount = it.id == id) })
        }
    }

    fun onSetDefaultPurchase(id: String) {
        if (_uiState.value.isEditMode) return
        _uiState.update { state ->
            state.copy(unitOptions = state.unitOptions.map { it.copy(isDefaultPurchase = it.id == id) })
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSubmitting) return
        
        val errors = mutableMapOf<String, Int>()
        if (state.name.isBlank()) errors["name"] = R.string.error_name_empty
        val par = state.parLevel.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val point = state.reorderPoint.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        if (state.parLevel.isNotBlank() && (par == null || par < BigDecimal.ZERO)) errors["par"] = R.string.reorder_nonnegative_error
        if (state.reorderPoint.isNotBlank() && (point == null || point < BigDecimal.ZERO)) errors["reorderPoint"] = R.string.reorder_nonnegative_error
        if (par != null && point != null && point > par) errors["reorderPoint"] = R.string.reorder_point_above_par_error
        
        if (state.ingredientId == null) {
            if (state.selectedDimension == null) errors["dimension"] = R.string.error_generic // Should specify
            if (state.selectedBaseUnitId == null) errors["baseUnit"] = R.string.error_generic // Should specify
        }

        val baseOption = state.unitOptions.find { it.isBase }
        if (baseOption == null) errors["options"] = R.string.error_generic
        
        val countDefaults = state.unitOptions.count { it.isDefaultCount }
        if (countDefaults != 1) errors["options"] = R.string.error_generic
        
        val purchaseDefaults = state.unitOptions.count { it.isDefaultPurchase }
        if (purchaseDefaults != 1) errors["options"] = R.string.error_generic

        // Names unique
        val names = state.unitOptions.map { it.name.normalizeName() }
        if (names.size != names.distinct().size) errors["options"] = R.string.error_duplicate_unit_option

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null, fieldErrors = emptyMap()) }
        
        viewModelScope.launch {
            try {
                // Read fresh state inside launch if needed, but here we just validated a snapshot.
                // Re-validating or using the snapshot is usually fine if we don't expect 
                // rapid changes during the tiny gap between validation and launch.
                val currentState = _uiState.value
                val restaurant = restaurantRepository.getRestaurant() ?: throw IllegalStateException("No restaurant")
                
                if (currentState.ingredientId == null) {
                    val newId = createIngredient(restaurant.id, currentState)
                    _events.send(IngredientFormEvent.Created(newId))
                } else {
                    updateIngredient(currentState)
                    _events.send(IngredientFormEvent.Updated(currentState.ingredientId))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e) }
            }
        }
    }

    private suspend fun createIngredient(restaurantId: RestaurantId, state: IngredientFormUiState): IngredientId {
        val ingredientId = IngredientId(idGenerator.newId())
        val now = timeProvider.now()
        
        val ingredient = Ingredient(
            id = ingredientId,
            restaurantId = restaurantId,
            name = state.name,
            normalizedName = "",
            categoryId = state.selectedCategoryId,
            baseUnitId = state.selectedBaseUnitId!!,
            defaultAreaId = state.selectedDefaultAreaId,
            parLevelBase = state.parLevel.takeIf(String::isNotBlank)?.let(::BigDecimal),
            reorderPointBase = state.reorderPoint.takeIf(String::isNotBlank)?.let(::BigDecimal),
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
        updateIngredientUseCase(
            UpdateIngredientCommand(
                ingredientId = ingredientId,
                name = state.name,
                categoryId = state.selectedCategoryId
                ,parLevelBase = state.parLevel.takeIf(String::isNotBlank)?.let(::BigDecimal)
                ,reorderPointBase = state.reorderPoint.takeIf(String::isNotBlank)?.let(::BigDecimal)
                ,defaultAreaId = state.selectedDefaultAreaId
            )
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getStandardPreview(unit: UnitOfMeasure): UnitConversionChoiceUiModel? {
        val state = _uiState.value
        val baseUnitId = state.selectedBaseUnitId ?: return null
        
        // This is a bit inefficient if called on every frame, but it's only in dialog
        // We'd ideally have the base UnitOfMeasure already in state
        val baseUnit = compatibleUnits.value.find { it.id == baseUnitId } ?: return null
        
        val factor = previewUnitConversionUseCase.preview(BigDecimal.ONE, unit, baseUnit)
        return UnitConversionChoiceUiModel(
            sourceSymbol = unit.symbol,
            factor = factor.stripTrailingZeros().toPlainString(),
            baseSymbol = baseUnit.symbol
        )
    }
}

private object EmptyIngredientAreaRepository : InventoryAreaRepository {
    override fun observeActiveAreas() = flowOf(emptyList<com.venkoi.cuentame.core.model.inventory.InventoryArea>())
    override fun observeAllAreas() = observeActiveAreas()
    override suspend fun getById(id: com.venkoi.cuentame.core.common.ids.InventoryAreaId) = null
    override suspend fun save(area: com.venkoi.cuentame.core.model.inventory.InventoryArea) = Unit
    override suspend fun archive(id: com.venkoi.cuentame.core.common.ids.InventoryAreaId, at: java.time.Instant) = Unit
    override suspend fun reorder(ids: List<com.venkoi.cuentame.core.common.ids.InventoryAreaId>) = Unit
}
