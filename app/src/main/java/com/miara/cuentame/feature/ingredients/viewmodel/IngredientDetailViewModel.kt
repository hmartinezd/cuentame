package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.usecase.AddPackageUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.AddStandardUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUseCase
import com.miara.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultCountUnitUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultPurchaseUnitUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePackageUnitOptionUseCase
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand
import com.miara.cuentame.core.domain.usecase.PreviewUnitConversionUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class IngredientDetailUiState(
    val isLoading: Boolean = true,
    val ingredient: Ingredient? = null,
    val category: IngredientCategory? = null,
    val options: List<IngredientUnitOption> = emptyList(),
    val compatibleUnits: List<UnitOfMeasure> = emptyList(),
    val baseUnit: UnitOfMeasure? = null,
    val isPerformingAction: Boolean = false,
    val error: Throwable? = null
)

sealed interface IngredientDetailEvent {
    data object IngredientArchived : IngredientDetailEvent
    data object StandardOptionAdded : IngredientDetailEvent
    data object PackageAdded : IngredientDetailEvent
    data object PackageUpdated : IngredientDetailEvent
    data class OptionArchived(val optionId: IngredientUnitOptionId) : IngredientDetailEvent
    data class CountDefaultChanged(val optionId: IngredientUnitOptionId) : IngredientDetailEvent
    data class PurchaseDefaultChanged(val optionId: IngredientUnitOptionId) : IngredientDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IngredientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ingredientRepository: IngredientRepository,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val observeCompatibleSystemUnitsUseCase: ObserveCompatibleSystemUnitsUseCase,
    private val observeIngredientCategoriesUseCase: com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase,
    private val unitRepository: UnitRepository,
    private val archiveIngredientUseCase: ArchiveIngredientUseCase,
    private val addStandardUnitOptionUseCase: AddStandardUnitOptionUseCase,
    private val addPackageUnitOptionUseCase: AddPackageUnitOptionUseCase,
    private val updatePackageUnitOptionUseCase: UpdatePackageUnitOptionUseCase,
    private val setDefaultCountUnitUseCase: SetDefaultCountUnitUseCase,
    private val setDefaultPurchaseUnitUseCase: SetDefaultPurchaseUnitUseCase,
    private val archiveIngredientUnitOptionUseCase: ArchiveIngredientUnitOptionUseCase,
    private val previewUnitConversionUseCase: PreviewUnitConversionUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val ingredientId: String? = savedStateHandle["ingredientId"]
    private val id = ingredientId?.let { IngredientId(it) }

    private val _isPerformingAction = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<IngredientDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val ingredientFlow = if (id == null) flowOf(null) else ingredientRepository.observeIngredient(id)

    private val categoryFlow = ingredientFlow.flatMapLatest { ingredient ->
        if (ingredient?.categoryId == null) flowOf(null)
        else observeIngredientCategoriesUseCase(activeOnly = false).map { list -> list.find { it.id == ingredient.categoryId } }
    }

    private val optionsFlow = if (id == null) flowOf(emptyList()) else observeIngredientUnitOptionsUseCase(id, includeArchived = true)

    private val compatibleUnitsFlow = ingredientFlow.flatMapLatest { ingredient ->
        if (ingredient == null) return@flatMapLatest flowOf(emptyList<UnitOfMeasure>())
        val baseUnit = unitRepository.getById(ingredient.baseUnitId)
        if (baseUnit == null) flowOf(emptyList())
        else observeCompatibleSystemUnitsUseCase(baseUnit.dimension)
    }

    private val baseUnitFlow = ingredientFlow.map { ingredient ->
        ingredient?.let { unitRepository.getById(it.baseUnitId) }
    }

    val uiState: StateFlow<IngredientDetailUiState> = combine(
        combine(ingredientFlow, categoryFlow, optionsFlow) { ing, cat, opt -> Triple(ing, cat, opt) },
        combine(compatibleUnitsFlow, baseUnitFlow, _isPerformingAction, _error) { units, base, performing, err ->
            Quad(units, base, performing, err)
        }
    ) { (ing, cat, opt), (units, base, performing, err) ->
        IngredientDetailUiState(
            isLoading = false,
            ingredient = ing,
            category = cat,
            options = opt,
            compatibleUnits = units,
            baseUnit = base,
            isPerformingAction = performing,
            error = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngredientDetailUiState()
    )

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun onArchiveIngredient() {
        val ingredientId = id ?: return
        performAction {
            archiveIngredientUseCase(ingredientId, timeProvider.now())
            _events.send(IngredientDetailEvent.IngredientArchived)
        }
    }

    fun onAddStandardOption(command: AddStandardUnitOptionCommand) {
        performAction {
            addStandardUnitOptionUseCase(command)
            _events.send(IngredientDetailEvent.StandardOptionAdded)
        }
    }

    fun onAddPackageOption(command: AddPackageUnitOptionCommand) {
        performAction {
            addPackageUnitOptionUseCase(command)
            _events.send(IngredientDetailEvent.PackageAdded)
        }
    }

    fun onUpdatePackageOption(command: UpdatePackageUnitOptionCommand) {
        performAction {
            updatePackageUnitOptionUseCase(command)
            _events.send(IngredientDetailEvent.PackageUpdated)
        }
    }

    fun onSetDefaultCount(optionId: IngredientUnitOptionId) {
        val ingredientId = id ?: return
        performAction {
            setDefaultCountUnitUseCase(ingredientId, optionId)
            _events.send(IngredientDetailEvent.CountDefaultChanged(optionId))
        }
    }

    fun onSetDefaultPurchase(optionId: IngredientUnitOptionId) {
        val ingredientId = id ?: return
        performAction {
            setDefaultPurchaseUnitUseCase(ingredientId, optionId)
            _events.send(IngredientDetailEvent.PurchaseDefaultChanged(optionId))
        }
    }

    fun onArchiveOption(optionId: IngredientUnitOptionId) {
        performAction {
            archiveIngredientUnitOptionUseCase(optionId, timeProvider.now())
            _events.send(IngredientDetailEvent.OptionArchived(optionId))
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun getStandardPreview(unit: UnitOfMeasure): com.miara.cuentame.feature.ingredients.model.UnitConversionChoiceUiModel? {
        val state = uiState.value
        val baseUnit = state.baseUnit ?: return null
        val factor = previewUnitConversionUseCase.preview(BigDecimal.ONE, unit, baseUnit)
        return com.miara.cuentame.feature.ingredients.model.UnitConversionChoiceUiModel(
            sourceSymbol = unit.symbol,
            factor = factor.stripTrailingZeros().toPlainString(),
            baseSymbol = baseUnit.symbol
        )
    }

    private fun performAction(block: suspend () -> Unit) {
        if (_isPerformingAction.value) return
        _isPerformingAction.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isPerformingAction.value = false
            }
        }
    }
}
