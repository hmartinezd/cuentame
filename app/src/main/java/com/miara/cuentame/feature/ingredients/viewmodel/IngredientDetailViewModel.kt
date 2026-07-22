package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.AddPackageUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.AddStandardUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUnitOptionUseCase
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUseCase
import com.miara.cuentame.core.domain.usecase.ObserveCompatibleSystemUnitsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultCountUnitUseCase
import com.miara.cuentame.core.domain.usecase.SetDefaultPurchaseUnitUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePackageUnitOptionUseCase
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand
import com.miara.cuentame.core.model.ingredient.Ingredient
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
import javax.inject.Inject

data class IngredientDetailUiState(
    val isLoading: Boolean = true,
    val ingredient: Ingredient? = null,
    val options: List<IngredientUnitOption> = emptyList(),
    val compatibleUnits: List<UnitOfMeasure> = emptyList(),
    val isPerformingAction: Boolean = false,
    val error: Throwable? = null
)

sealed interface IngredientDetailEvent {
    data object ArchiveSuccess : IngredientDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IngredientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeIngredientsUseCase: ObserveIngredientsUseCase,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val observeCompatibleSystemUnitsUseCase: ObserveCompatibleSystemUnitsUseCase,
    private val unitRepository: com.miara.cuentame.core.domain.repository.UnitRepository,
    private val archiveIngredientUseCase: ArchiveIngredientUseCase,
    private val addStandardUnitOptionUseCase: AddStandardUnitOptionUseCase,
    private val addPackageUnitOptionUseCase: AddPackageUnitOptionUseCase,
    private val updatePackageUnitOptionUseCase: UpdatePackageUnitOptionUseCase,
    private val setDefaultCountUnitUseCase: SetDefaultCountUnitUseCase,
    private val setDefaultPurchaseUnitUseCase: SetDefaultPurchaseUnitUseCase,
    private val archiveIngredientUnitOptionUseCase: ArchiveIngredientUnitOptionUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val ingredientId: String = checkNotNull(savedStateHandle["ingredientId"])
    private val id = IngredientId(ingredientId)

    private val _isPerformingAction = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<IngredientDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val ingredientFlow = observeIngredientsUseCase(includeArchived = true)
        .map { list -> list.find { it.id == id } }

    private val compatibleUnitsFlow = ingredientFlow.flatMapLatest { ingredient ->
        if (ingredient == null) return@flatMapLatest flowOf(emptyList<UnitOfMeasure>())
        val baseUnit = unitRepository.getById(ingredient.baseUnitId)
        if (baseUnit == null) flowOf(emptyList())
        else observeCompatibleSystemUnitsUseCase(baseUnit.dimension)
    }

    val uiState: StateFlow<IngredientDetailUiState> = combine(
        ingredientFlow,
        observeIngredientUnitOptionsUseCase(id),
        compatibleUnitsFlow,
        _isPerformingAction,
        _error
    ) { ingredient, options, compatibleUnits, isPerforming, error ->
        IngredientDetailUiState(
            isLoading = false,
            ingredient = ingredient,
            options = options,
            compatibleUnits = compatibleUnits,
            isPerformingAction = isPerforming,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngredientDetailUiState()
    )

    fun onArchiveIngredient() {
        performAction {
            archiveIngredientUseCase(id, timeProvider.now())
            _events.send(IngredientDetailEvent.ArchiveSuccess)
        }
    }

    fun onAddStandardOption(command: AddStandardUnitOptionCommand) {
        performAction {
            addStandardUnitOptionUseCase(command)
        }
    }

    fun onAddPackageOption(command: AddPackageUnitOptionCommand) {
        performAction {
            addPackageUnitOptionUseCase(command)
        }
    }

    fun onUpdatePackageOption(command: UpdatePackageUnitOptionCommand) {
        performAction {
            updatePackageUnitOptionUseCase(command)
        }
    }

    fun onSetDefaultCount(optionId: IngredientUnitOptionId) {
        performAction {
            setDefaultCountUnitUseCase(id, optionId)
        }
    }

    fun onSetDefaultPurchase(optionId: IngredientUnitOptionId) {
        performAction {
            setDefaultPurchaseUnitUseCase(id, optionId)
        }
    }

    fun onArchiveOption(optionId: IngredientUnitOptionId) {
        performAction {
            archiveIngredientUnitOptionUseCase(optionId, timeProvider.now())
        }
    }

    fun clearError() {
        _error.value = null
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
