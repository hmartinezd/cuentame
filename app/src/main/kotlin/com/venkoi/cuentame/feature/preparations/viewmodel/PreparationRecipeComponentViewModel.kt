package com.venkoi.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeComponentId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.domain.repository.*
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipe
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.cuentame.feature.preparations.presentation.toPreparationRecipeUserMessage
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PreparationRecipeComponentEvent {
    data object Saved : PreparationRecipeComponentEvent
    data class NavigateToDetail(val recipeId: PreparationRecipeId) : PreparationRecipeComponentEvent
}

sealed interface PreparationRecipeComponentMode {
    data object Create : PreparationRecipeComponentMode
    data class Edit(val componentId: PreparationRecipeComponentId) : PreparationRecipeComponentMode
}

data class PreparationRecipeComponentUiState(
    val loadState: PreparationScreenLoadState = PreparationScreenLoadState.Loading,
    val mode: PreparationRecipeComponentMode = PreparationRecipeComponentMode.Create,
    val isSaving: Boolean = false,
    val recipe: PreparationRecipe? = null,
    val availableIngredients: List<Ingredient> = emptyList(),
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    // Form state
    val selectedIngredient: Ingredient? = null,
    val quantity: String = "",
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val notes: String = "",
    // Validation
    val quantityError: Boolean = false,
    val quantityErrorText: UiMessage? = null,
    val inlineError: UiMessage? = null,
    val showDiscardConfirmation: Boolean = false
)

@HiltViewModel
class PreparationRecipeComponentViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId = savedStateHandle.get<String>("recipeId")
        ?.takeIf { it.isNotBlank() }
        ?.let { PreparationRecipeId(it) }

    private val componentId = savedStateHandle.get<String>("componentId")
        ?.takeIf { it.isNotBlank() }
        ?.let { PreparationRecipeComponentId(it) }

    private val _uiState = MutableStateFlow(
        PreparationRecipeComponentUiState(
            mode = componentId?.let { PreparationRecipeComponentMode.Edit(it) } ?: PreparationRecipeComponentMode.Create
        )
    )
    val uiState: StateFlow<PreparationRecipeComponentUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreparationRecipeComponentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var isInitialized = false
    private val retryTrigger = MutableStateFlow(0)
    private val unitOptionsRequests = MutableSharedFlow<Ingredient>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var nonDraftNavigationEmitted = false

    init {
        observeData()
        observeUnitOptions()
    }

    private fun observeData() {
        viewModelScope.launch {
            retryTrigger.collectLatest {
                loadData()
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun loadData() {
        if (recipeId == null) {
            _uiState.update { it.copy(loadState = PreparationScreenLoadState.InvalidRoute) }
            return
        }

        _uiState.update { it.copy(loadState = PreparationScreenLoadState.Loading) }

        try {
            val restaurant = restaurantRepository.getRestaurant()
            if (restaurant == null) {
                _uiState.update { it.copy(loadState = PreparationScreenLoadState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                return
            }

            preparationRecipeRepository.observeRecipe(recipeId).collectLatest { recipe ->
                if (recipe == null) {
                    _uiState.update { it.copy(loadState = PreparationScreenLoadState.RecipeNotFound) }
                    return@collectLatest
                }

                if (recipe.status != PreparationRecipeStatus.DRAFT) {
                    if (!nonDraftNavigationEmitted) {
                        nonDraftNavigationEmitted = true
                        _events.send(PreparationRecipeComponentEvent.NavigateToDetail(recipe.id))
                    }
                    _uiState.update { it.copy(loadState = PreparationScreenLoadState.ParentNotEditable) }
                    return@collectLatest
                }

                val ingredients = try {
                    ingredientRepository.getIngredients(restaurant.id, includeArchived = false)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    _uiState.update { it.copy(loadState = PreparationScreenLoadState.LoadError(e.toPreparationRecipeUserMessage())) }
                    return@collectLatest
                }

                val usedIngredientIds = recipe.components
                    .filter { if (componentId != null) it.id != componentId else true }
                    .map { it.componentIngredientId }
                    .toSet()

                val availableIngredients = ingredients.filter {
                    it.id != recipe.outputIngredientId && it.id !in usedIngredientIds
                }

                if (!isInitialized) {
                    val existingComp = if (componentId != null) recipe.components.find { it.id == componentId } else null
                    if (componentId != null && existingComp == null) {
                        _uiState.update { it.copy(loadState = PreparationScreenLoadState.ComponentNotFound) }
                        return@collectLatest
                    }

                    if (existingComp != null) {
                        val unitOptions = try {
                            ingredientRepository.getUnitOptions(existingComp.componentIngredientId, includeArchived = false)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (e: Exception) {
                            _uiState.update {
                                it.copy(
                                    loadState = PreparationScreenLoadState.LoadError(e.toPreparationRecipeUserMessage())
                                )
                            }
                            return@collectLatest
                        }
                        _uiState.update {
                            it.copy(
                                loadState = PreparationScreenLoadState.EditReady,
                                recipe = recipe,
                                availableIngredients = availableIngredients,
                                availableUnitOptions = unitOptions,
                                selectedIngredient = ingredients.find { i -> i.id == existingComp.componentIngredientId },
                                quantity = existingComp.quantityEntered.toPlainString(),
                                selectedUnitOptionId = existingComp.unitOptionId,
                                notes = existingComp.notes ?: ""
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                loadState = PreparationScreenLoadState.CreateReady,
                                recipe = recipe,
                                availableIngredients = availableIngredients
                            )
                        }
                    }
                    isInitialized = true
                } else {
                    val readyState = when (val currentMode = _uiState.value.mode) {
                        PreparationRecipeComponentMode.Create -> PreparationScreenLoadState.CreateReady
                        is PreparationRecipeComponentMode.Edit -> PreparationScreenLoadState.EditReady
                    }
                    _uiState.update {
                        it.copy(
                            loadState = readyState,
                            recipe = recipe,
                            availableIngredients = availableIngredients
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            _uiState.update { it.copy(loadState = PreparationScreenLoadState.LoadError(e.toPreparationRecipeUserMessage())) }
        }
    }

    private fun observeUnitOptions() {
        viewModelScope.launch {
            unitOptionsRequests.collectLatest { ingredient ->
                try {
                    val unitOptions = ingredientRepository.getUnitOptions(ingredient.id, includeArchived = false)
                    if (_uiState.value.selectedIngredient?.id == ingredient.id) {
                        _uiState.update {
                            it.copy(
                                availableUnitOptions = unitOptions,
                                selectedUnitOptionId = if (it.selectedUnitOptionId !in unitOptions.map { o -> o.id }) null else it.selectedUnitOptionId,
                                inlineError = null
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    if (_uiState.value.selectedIngredient?.id == ingredient.id) {
                        _uiState.update {
                            it.copy(
                                availableUnitOptions = emptyList(),
                                selectedUnitOptionId = null,
                                inlineError = e.toPreparationRecipeUserMessage()
                            )
                        }
                    }
                }
            }
        }
    }

    fun onIngredientSelected(ingredient: Ingredient) {
        _uiState.update { it.copy(selectedIngredient = ingredient) }
        unitOptionsRequests.tryEmit(ingredient)
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity, quantityError = false, quantityErrorText = null) }
    }

    fun onUnitOptionSelected(option: IngredientUnitOption) {
        _uiState.update { it.copy(selectedUnitOptionId = option.id) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSave() {
        val recipeIdVal = recipeId ?: return
        val state = _uiState.value
        if (state.isSaving) return
        if (state.selectedIngredient == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_select_component_ingredient)) }
            return
        }
        if (state.selectedUnitOptionId == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_select_unit)) }
            return
        }

        val parsedQuantity = try {
            BigDecimal(state.quantity)
        } catch (e: Exception) {
            _uiState.update { it.copy(quantityError = true, quantityErrorText = UiMessage.Resource(R.string.error_invalid_decimal)) }
            return
        }

        if (parsedQuantity <= BigDecimal.ZERO) {
            _uiState.update { it.copy(quantityError = true, quantityErrorText = UiMessage.Resource(R.string.error_quantity_positive)) }
            return
        }

        val recipe = state.recipe ?: return
        val existingComp = if (componentId != null) recipe.components.find { it.id == componentId } else null
        val sortOrder = existingComp?.sortOrder ?: ((recipe.components.maxOfOrNull { it.sortOrder } ?: -1) + 1)

        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        viewModelScope.launch {
            try {
                preparationRecipeRepository.saveComponent(
                    SavePreparationRecipeComponentCommand(
                        recipeId = recipeIdVal,
                        componentId = componentId,
                        componentIngredientId = state.selectedIngredient.id,
                        unitOptionId = state.selectedUnitOptionId,
                        quantityEntered = parsedQuantity,
                        sortOrder = sortOrder,
                        notes = state.notes.trim().ifBlank { null }
                    )
                )
                _events.send(PreparationRecipeComponentEvent.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.toPreparationRecipeUserMessage()) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        val mode = state.mode
        
        return if (mode is PreparationRecipeComponentMode.Create) {
            state.selectedIngredient != null ||
            state.quantity.isNotBlank() ||
            state.selectedUnitOptionId != null ||
            state.notes.isNotBlank()
        } else {
            val existingComp = state.recipe?.components?.find { it.id == (mode as PreparationRecipeComponentMode.Edit).componentId }
            state.selectedIngredient?.id != existingComp?.componentIngredientId ||
            state.quantity != (existingComp?.quantityEntered?.toPlainString() ?: "") ||
            state.selectedUnitOptionId != existingComp?.unitOptionId ||
            state.notes != (existingComp?.notes ?: "")
        }
    }

    fun onBackAction(onConfirmBack: () -> Unit) {
        if (hasUnsavedChanges()) {
            _uiState.update { it.copy(showDiscardConfirmation = true) }
        } else {
            onConfirmBack()
        }
    }

    fun dismissDiscardConfirmation() {
        _uiState.update { it.copy(showDiscardConfirmation = false) }
    }
}
