package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.feature.preparations.presentation.toPreparationRecipeUserMessage
import com.miara.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PreparationRecipeEditorEvent {
    data class Created(val recipeId: PreparationRecipeId) : PreparationRecipeEditorEvent
    data object DraftSaved : PreparationRecipeEditorEvent
    data class NavigateToDetail(val recipeId: PreparationRecipeId) : PreparationRecipeEditorEvent
}

data class PreparationRecipeEditorUiState(
    val loadState: PreparationScreenLoadState = PreparationScreenLoadState.Loading,
    val isSaving: Boolean = false,
    val isReordering: Boolean = false,
    val isActivating: Boolean = false,
    val recipe: PreparationRecipe? = null,
    val availableIngredients: List<Ingredient> = emptyList(),
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    val componentNames: Map<PreparationRecipeComponentId, String> = emptyMap(),
    val componentUnitLabels: Map<PreparationRecipeComponentId, String> = emptyMap(),
    // Form state
    val selectedOutputIngredient: Ingredient? = null,
    val recipeName: String = "",
    val yieldQuantity: String = "",
    val selectedYieldUnitOptionId: com.miara.cuentame.core.common.ids.IngredientUnitOptionId? = null,
    val notes: String = "",
    // Validation
    val yieldQuantityError: Boolean = false,
    val yieldQuantityErrorText: UiMessage? = null,
    val inlineError: UiMessage? = null,
    val showDiscardConfirmation: Boolean = false,
    val showActivateConfirmation: Boolean = false
) {
    val isOperating: Boolean get() = isSaving || isReordering || isActivating
}

@HiltViewModel
class PreparationRecipeEditorViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId = savedStateHandle.get<String>("recipeId")
        ?.takeIf { it.isNotBlank() }
        ?.let { PreparationRecipeId(it) }

    private val _uiState = MutableStateFlow(PreparationRecipeEditorUiState())
    val uiState: StateFlow<PreparationRecipeEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreparationRecipeEditorEvent>(Channel.BUFFERED)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewModelScope.launch {
            retryTrigger.collectLatest {
                loadData()
            }
        }
    }

    private suspend fun loadData() {
        _uiState.update { it.copy(loadState = PreparationScreenLoadState.Loading) }
        try {
            val restaurant = restaurantRepository.getRestaurant()
            if (restaurant == null) {
                _uiState.update { it.copy(loadState = PreparationScreenLoadState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                return
            }

            val ingredientsFlow = ingredientRepository.observeIngredients(restaurant.id, includeArchived = false)
            val recipesFlow = preparationRecipeRepository.observeRecipes(restaurant.id, includeArchived = false)

            val recipeFlow = if (recipeId != null) {
                preparationRecipeRepository.observeRecipe(recipeId)
            } else {
                flowOf(null)
            }

            combine(ingredientsFlow, recipesFlow, recipeFlow) { ingredients, recipes, recipe ->
                Triple(ingredients, recipes, recipe)
            }.collectLatest { (ingredients, recipes, recipe) ->
                if (recipeId != null && recipe == null) {
                    _uiState.update { it.copy(loadState = PreparationScreenLoadState.RecipeNotFound) }
                    return@collectLatest
                }

                if (recipe != null && recipe.status != PreparationRecipeStatus.DRAFT) {
                    if (!nonDraftNavigationEmitted) {
                        nonDraftNavigationEmitted = true
                        _events.send(PreparationRecipeEditorEvent.NavigateToDetail(recipe.id))
                    }
                    return@collectLatest
                }

                val alreadyUsedIds = recipes
                    .filter { if (recipeId != null) it.id != recipeId else true }
                    .map { it.outputIngredientId }
                    .toSet()

                val availableIngredients = ingredients.filter { it.id !in alreadyUsedIds }

                val componentNames = mutableMapOf<PreparationRecipeComponentId, String>()
                val componentUnitLabels = mutableMapOf<PreparationRecipeComponentId, String>()

                if (recipe != null) {
                    for (comp in recipe.components) {
                        componentNames[comp.id] = ingredients.find { it.id == comp.componentIngredientId }?.name ?: comp.componentIngredientId.value
                        val options = ingredientRepository.getUnitOptions(comp.componentIngredientId, includeArchived = true)
                        componentUnitLabels[comp.id] = options.find { it.id == comp.unitOptionId }?.displayName ?: comp.unitOptionId.value
                    }
                }

                if (!isInitialized) {
                    if (recipeId != null && recipe != null) {
                        val unitOptions = try {
                            ingredientRepository.getUnitOptions(recipe.outputIngredientId, includeArchived = false)
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
                                componentNames = componentNames,
                                componentUnitLabels = componentUnitLabels,
                                selectedOutputIngredient = ingredients.find { i -> i.id == recipe.outputIngredientId },
                                recipeName = recipe.name,
                                yieldQuantity = recipe.standardYieldQuantity?.toPlainString() ?: "",
                                selectedYieldUnitOptionId = recipe.yieldUnitOptionId,
                                notes = recipe.notes ?: ""
                            )
                        }
                        isInitialized = true
                    } else {
                        _uiState.update {
                            it.copy(
                                loadState = PreparationScreenLoadState.CreateReady,
                                availableIngredients = availableIngredients
                            )
                        }
                        isInitialized = true
                    }
                } else {
                    // Refresh data but preserve form state
                    val readyState = if (recipeId == null) {
                        PreparationScreenLoadState.CreateReady
                    } else {
                        PreparationScreenLoadState.EditReady
                    }
                    _uiState.update {
                        it.copy(
                            loadState = readyState,
                            recipe = recipe,
                            availableIngredients = availableIngredients,
                            componentNames = componentNames,
                            componentUnitLabels = componentUnitLabels
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUnitOptions() {
        viewModelScope.launch {
            unitOptionsRequests.collectLatest { ingredient ->
                try {
                    val unitOptions = ingredientRepository.getUnitOptions(ingredient.id, includeArchived = false)
                    if (_uiState.value.selectedOutputIngredient?.id == ingredient.id) {
                        _uiState.update {
                            it.copy(
                                availableUnitOptions = unitOptions,
                                selectedYieldUnitOptionId = if (it.selectedYieldUnitOptionId !in unitOptions.map { o -> o.id }) null else it.selectedYieldUnitOptionId,
                                inlineError = null
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    if (_uiState.value.selectedOutputIngredient?.id == ingredient.id) {
                        _uiState.update {
                            it.copy(
                                availableUnitOptions = emptyList(),
                                selectedYieldUnitOptionId = null,
                                inlineError = e.toPreparationRecipeUserMessage()
                            )
                        }
                    }
                }
            }
        }
    }

    fun onOutputIngredientSelected(ingredient: Ingredient) {
        _uiState.update { it.copy(selectedOutputIngredient = ingredient) }
        unitOptionsRequests.tryEmit(ingredient)
    }

    fun onRecipeNameChanged(name: String) {
        _uiState.update { it.copy(recipeName = name) }
    }

    fun onYieldQuantityChanged(quantity: String) {
        _uiState.update { it.copy(yieldQuantity = quantity, yieldQuantityError = false, yieldQuantityErrorText = null, inlineError = null) }
    }

    fun onYieldUnitOptionSelected(option: IngredientUnitOption) {
        _uiState.update { it.copy(selectedYieldUnitOptionId = option.id, inlineError = null) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSave() {
        viewModelScope.launch {
            val result = saveInternal()
            if (result is DraftSaveResult.Success) {
                if (recipeId == null) {
                    _events.send(PreparationRecipeEditorEvent.Created(result.recipeId))
                } else {
                    _events.send(PreparationRecipeEditorEvent.DraftSaved)
                }
            }
        }
    }

    private sealed interface DraftSaveResult {
        data class Success(val recipeId: PreparationRecipeId) : DraftSaveResult
        data class Failure(val message: UiMessage) : DraftSaveResult
    }

    private suspend fun saveInternal(): DraftSaveResult {
        val state = _uiState.value
        if (state.isOperating) return DraftSaveResult.Failure(UiMessage.Resource(R.string.error_generic))
        if (state.selectedOutputIngredient == null) {
            val msg = UiMessage.Resource(R.string.error_select_output_ingredient)
            _uiState.update { it.copy(inlineError = msg) }
            return DraftSaveResult.Failure(msg)
        }

        val quantity = if (state.yieldQuantity.isBlank()) null else {
            try {
                BigDecimal(state.yieldQuantity)
            } catch (e: Exception) {
                val msg = UiMessage.Resource(R.string.error_invalid_decimal)
                _uiState.update { it.copy(yieldQuantityError = true, yieldQuantityErrorText = msg, inlineError = null) }
                return DraftSaveResult.Failure(msg)
            }
        }

        if (quantity != null && quantity <= BigDecimal.ZERO) {
            val msg = UiMessage.Resource(R.string.error_quantity_positive)
            _uiState.update { it.copy(yieldQuantityError = true, yieldQuantityErrorText = msg, inlineError = null) }
            return DraftSaveResult.Failure(msg)
        }

        // Standard yield must have both quantity and unit if either is present
        if ((quantity != null && state.selectedYieldUnitOptionId == null) || (quantity == null && state.selectedYieldUnitOptionId != null)) {
            val msg = UiMessage.Resource(R.string.error_yield_pairing_required)
            _uiState.update { it.copy(inlineError = msg) }
            return DraftSaveResult.Failure(msg)
        }

        val trimmedName = state.recipeName.trim().ifBlank { null }
        if (recipeId != null && trimmedName == null) {
            val msg = UiMessage.Resource(R.string.error_recipe_name_required)
            _uiState.update { it.copy(inlineError = msg) }
            return DraftSaveResult.Failure(msg)
        }

        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        return try {
            val restaurant = restaurantRepository.getRestaurant()
            if (restaurant == null) {
                val msg = UiMessage.Resource(R.string.error_generic)
                _uiState.update { it.copy(inlineError = msg) }
                return DraftSaveResult.Failure(msg)
            }

            val savedId = if (recipeId == null) {
                preparationRecipeRepository.createDraft(
                    CreatePreparationRecipeCommand(
                        restaurantId = restaurant.id,
                        outputIngredientId = state.selectedOutputIngredient.id,
                        name = trimmedName,
                        standardYieldQuantity = quantity,
                        yieldUnitOptionId = state.selectedYieldUnitOptionId,
                        notes = state.notes.trim().ifBlank { null }
                    )
                )
            } else {
                preparationRecipeRepository.updateDraft(
                    UpdatePreparationRecipeCommand(
                        recipeId = recipeId,
                        name = trimmedName ?: "",
                        standardYieldQuantity = quantity,
                        yieldUnitOptionId = state.selectedYieldUnitOptionId,
                        notes = state.notes.trim().ifBlank { null }
                    )
                )
                recipeId
            }
            DraftSaveResult.Success(savedId)
        } catch (e: Exception) {
            val msg = e.toPreparationRecipeUserMessage()
            _uiState.update { it.copy(inlineError = msg) }
            DraftSaveResult.Failure(msg)
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun onActivateClick() {
        if (_uiState.value.isOperating) return
        _uiState.update { it.copy(showActivateConfirmation = true) }
    }

    fun onActivateConfirm() {
        val rId = recipeId ?: return
        if (_uiState.value.isOperating) return
        _uiState.update { it.copy(showActivateConfirmation = false, isActivating = true, inlineError = null) }
        viewModelScope.launch {
            try {
                // 1. Save pending changes if needed
                if (hasUnsavedChanges()) {
                    val saveResult = saveInternal()
                    if (saveResult is DraftSaveResult.Failure) {
                        // Saving failed, error already shown in inlineError
                        return@launch
                    }
                }

                // 2. Activate
                preparationRecipeRepository.activate(rId)
                // Navigation will be handled by the observer in loadData() when status changes to ACTIVE
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.toPreparationRecipeUserMessage()) }
            } finally {
                _uiState.update { it.copy(isActivating = false) }
            }
        }
    }

    fun dismissActivateConfirmation() {
        _uiState.update { it.copy(showActivateConfirmation = false) }
    }

    fun onRemoveComponent(componentId: PreparationRecipeComponentId) {
        val rId = recipeId ?: return
        if (_uiState.value.isOperating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, inlineError = null) }
            try {
                preparationRecipeRepository.removeComponent(rId, componentId)
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.toPreparationRecipeUserMessage()) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onMoveComponentUp(componentId: PreparationRecipeComponentId) {
        moveComponent(componentId, -1)
    }

    fun onMoveComponentDown(componentId: PreparationRecipeComponentId) {
        moveComponent(componentId, 1)
    }

    private fun moveComponent(componentId: PreparationRecipeComponentId, delta: Int) {
        val rId = recipeId ?: return
        if (_uiState.value.isOperating) return
        val currentRecipe = _uiState.value.recipe ?: return
        val components = currentRecipe.components.sortedBy { it.sortOrder }
        val index = components.indexOfFirst { it.id == componentId }
        if (index == -1) return
        
        val newIndex = index + delta
        if (newIndex !in components.indices) return

        val mutableComponents = components.toMutableList()
        val item = mutableComponents.removeAt(index)
        mutableComponents.add(newIndex, item)

        viewModelScope.launch {
            _uiState.update { it.copy(isReordering = true, inlineError = null) }
            try {
                preparationRecipeRepository.reorderComponents(rId, mutableComponents.map { it.id })
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.toPreparationRecipeUserMessage()) }
            } finally {
                _uiState.update { it.copy(isReordering = false) }
            }
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        val recipe = state.recipe
        
        return if (recipe == null) {
            // Creation mode - consider it dirty if any field is touched
            state.selectedOutputIngredient != null ||
            state.recipeName.isNotBlank() ||
            state.yieldQuantity.isNotBlank() ||
            state.selectedYieldUnitOptionId != null ||
            state.notes.isNotBlank()
        } else {
            // Edit mode - compare with original recipe values
            state.recipeName != recipe.name ||
            state.yieldQuantity != (recipe.standardYieldQuantity?.toPlainString() ?: "") ||
            state.selectedYieldUnitOptionId != recipe.yieldUnitOptionId ||
            state.notes != (recipe.notes ?: "")
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
