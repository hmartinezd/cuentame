package com.venkoi.restaurantops.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.PreparationRecipeComponentId
import com.venkoi.restaurantops.core.common.ids.PreparationRecipeId
import com.venkoi.restaurantops.core.domain.repository.*
import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import com.venkoi.restaurantops.core.model.ingredient.IngredientUnitOption
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipe
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.restaurantops.feature.preparations.presentation.toPreparationRecipeUserMessage
import com.venkoi.restaurantops.core.presentation.ui.UiMessage
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
    val selectedYieldUnitOptionId: com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId? = null,
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
        if (_uiState.value.isOperating) return

        val validation = validateForm()
        if (validation is FormValidationResult.Invalid) {
            _uiState.update { state ->
                val isYieldError = validation.message is UiMessage.Resource &&
                    (validation.message.id == R.string.error_invalid_decimal || 
                     validation.message.id == R.string.error_quantity_positive)
                
                state.copy(
                    inlineError = validation.message,
                    yieldQuantityError = isYieldError,
                    yieldQuantityErrorText = if (isYieldError && validation.message is UiMessage.Resource) validation.message else null
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, inlineError = null) }
        viewModelScope.launch {
            val result = persistDraft((validation as FormValidationResult.Valid).form)
            _uiState.update { it.copy(isSaving = false) }

            if (result is DraftPersistenceResult.Success) {
                if (recipeId == null) {
                    _events.send(PreparationRecipeEditorEvent.Created(result.recipeId))
                } else {
                    _events.send(PreparationRecipeEditorEvent.DraftSaved)
                }
            } else if (result is DraftPersistenceResult.Failure) {
                _uiState.update { it.copy(inlineError = result.message) }
            }
        }
    }

    private data class ValidatedRecipeForm(
        val outputIngredientId: IngredientId,
        val name: String?,
        val standardYieldQuantity: BigDecimal?,
        val yieldUnitOptionId: com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId?,
        val notes: String?
    )

    private sealed interface FormValidationResult {
        data class Valid(val form: ValidatedRecipeForm) : FormValidationResult
        data class Invalid(val message: UiMessage) : FormValidationResult
    }

    private sealed interface DraftPersistenceResult {
        data class Success(val recipeId: PreparationRecipeId) : DraftPersistenceResult
        data class Failure(val message: UiMessage) : DraftPersistenceResult
    }

    private fun validateForm(): FormValidationResult {
        val state = _uiState.value
        if (state.selectedOutputIngredient == null) {
            return FormValidationResult.Invalid(UiMessage.Resource(R.string.error_select_output_ingredient))
        }

        val quantity = if (state.yieldQuantity.isBlank()) null else {
            try {
                BigDecimal(state.yieldQuantity)
            } catch (e: Exception) {
                return FormValidationResult.Invalid(UiMessage.Resource(R.string.error_invalid_decimal))
            }
        }

        if (quantity != null && quantity <= BigDecimal.ZERO) {
            return FormValidationResult.Invalid(UiMessage.Resource(R.string.error_quantity_positive))
        }

        // Standard yield must have both quantity and unit if either is present
        if ((quantity != null && state.selectedYieldUnitOptionId == null) || (quantity == null && state.selectedYieldUnitOptionId != null)) {
            return FormValidationResult.Invalid(UiMessage.Resource(R.string.error_yield_pairing_required))
        }

        val trimmedName = state.recipeName.trim().ifBlank { null }
        if (recipeId != null && trimmedName == null) {
            return FormValidationResult.Invalid(UiMessage.Resource(R.string.error_recipe_name_required))
        }

        return FormValidationResult.Valid(
            ValidatedRecipeForm(
                outputIngredientId = state.selectedOutputIngredient.id,
                name = trimmedName,
                standardYieldQuantity = quantity,
                yieldUnitOptionId = state.selectedYieldUnitOptionId,
                notes = state.notes.trim().ifBlank { null }
            )
        )
    }

    private suspend fun persistDraft(form: ValidatedRecipeForm): DraftPersistenceResult {
        return try {
            val restaurant = restaurantRepository.getRestaurant()
                ?: return DraftPersistenceResult.Failure(UiMessage.Resource(R.string.error_generic))

            val savedId = if (recipeId == null) {
                preparationRecipeRepository.createDraft(
                    CreatePreparationRecipeCommand(
                        restaurantId = restaurant.id,
                        outputIngredientId = form.outputIngredientId,
                        name = form.name,
                        standardYieldQuantity = form.standardYieldQuantity,
                        yieldUnitOptionId = form.yieldUnitOptionId,
                        notes = form.notes
                    )
                )
            } else {
                preparationRecipeRepository.updateDraft(
                    UpdatePreparationRecipeCommand(
                        recipeId = recipeId,
                        name = form.name ?: "",
                        standardYieldQuantity = form.standardYieldQuantity,
                        yieldUnitOptionId = form.yieldUnitOptionId,
                        notes = form.notes
                    )
                )
                recipeId
            }
            DraftPersistenceResult.Success(savedId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DraftPersistenceResult.Failure(e.toPreparationRecipeUserMessage())
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
                // 1. Validate
                val validation = validateForm()
                if (validation is FormValidationResult.Invalid) {
                    _uiState.update { it.copy(inlineError = validation.message, isActivating = false) }
                    return@launch
                }

                // 2. Persist pending changes if necessary
                if (hasUnsavedChanges()) {
                    val persistResult = persistDraft((validation as FormValidationResult.Valid).form)
                    if (persistResult is DraftPersistenceResult.Failure) {
                        _uiState.update { it.copy(inlineError = persistResult.message, isActivating = false) }
                        return@launch
                    }
                }

                // 3. Activate
                preparationRecipeRepository.activate(rId)
                // Navigation will be handled by the observer in loadData() when status changes to ACTIVE
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(inlineError = e.toPreparationRecipeUserMessage(), isActivating = false) }
            }
            // Note: isActivating remains true if successful until navigation triggers destination disposal
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
