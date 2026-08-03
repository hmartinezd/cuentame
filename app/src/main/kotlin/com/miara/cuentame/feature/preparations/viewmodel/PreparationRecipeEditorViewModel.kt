package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PreparationRecipeEditorEvent {
    data class Created(val recipeId: PreparationRecipeId) : PreparationRecipeEditorEvent
    data object Saved : PreparationRecipeEditorEvent
    data object DeletedOrArchived : PreparationRecipeEditorEvent
}

data class PreparationRecipeEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isReordering: Boolean = false,
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
    val error: Throwable? = null,
    val inlineError: String? = null
)

@HiltViewModel
class PreparationRecipeEditorViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeIdStr: String? = savedStateHandle["recipeId"]
    private val recipeId = recipeIdStr?.let { PreparationRecipeId(it) }

    private val _uiState = MutableStateFlow(PreparationRecipeEditorUiState())
    val uiState: StateFlow<PreparationRecipeEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreparationRecipeEditorEvent>()
    val events = _events.receiveAsFlow()

    private var isInitialized = false

    init {
        loadData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            val restaurant = restaurantRepository.getRestaurant() ?: return@launch
            
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
                        // This is inefficient but necessary because IngredientUnitOption labels are not in PreparationRecipeComponent
                        val options = ingredientRepository.getUnitOptions(comp.componentIngredientId, includeArchived = true)
                        componentUnitLabels[comp.id] = options.find { it.id == comp.unitOptionId }?.displayName ?: comp.unitOptionId.value
                    }
                }

                if (!isInitialized) {
                    if (recipe != null) {
                        val unitOptions = ingredientRepository.getUnitOptions(recipe.outputIngredientId, includeArchived = false)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
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
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                availableIngredients = availableIngredients
                            )
                        }
                    }
                    isInitialized = true
                } else {
                    // Refresh data but preserve form state
                    _uiState.update {
                        it.copy(
                            recipe = recipe,
                            availableIngredients = availableIngredients,
                            componentNames = componentNames,
                            componentUnitLabels = componentUnitLabels
                        )
                    }
                }
            }
        }
    }

    fun onOutputIngredientSelected(ingredient: Ingredient) {
        viewModelScope.launch {
            val unitOptions = ingredientRepository.getUnitOptions(ingredient.id, includeArchived = false)
            _uiState.update {
                it.copy(
                    selectedOutputIngredient = ingredient,
                    availableUnitOptions = unitOptions,
                    selectedYieldUnitOptionId = if (it.selectedYieldUnitOptionId !in unitOptions.map { o -> o.id }) null else it.selectedYieldUnitOptionId
                )
            }
        }
    }

    fun onRecipeNameChanged(name: String) {
        _uiState.update { it.copy(recipeName = name) }
    }

    fun onYieldQuantityChanged(quantity: String) {
        _uiState.update { it.copy(yieldQuantity = quantity, yieldQuantityError = false) }
    }

    fun onYieldUnitOptionSelected(option: IngredientUnitOption) {
        _uiState.update { it.copy(selectedYieldUnitOptionId = option.id) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.selectedOutputIngredient == null) return

        val quantity = if (state.yieldQuantity.isBlank()) null else {
            try {
                BigDecimal(state.yieldQuantity)
            } catch (e: Exception) {
                _uiState.update { it.copy(yieldQuantityError = true) }
                return
            }
        }

        if (quantity != null && quantity <= BigDecimal.ZERO) {
            _uiState.update { it.copy(yieldQuantityError = true) }
            return
        }

        // Standard yield must have both quantity and unit if either is present
        if ((quantity != null && state.selectedYieldUnitOptionId == null) || (quantity == null && state.selectedYieldUnitOptionId != null)) {
            // Handled by UI validation or repository? Requirement 8 says "quantity and unit must be supplied together"
            _uiState.update { it.copy(inlineError = "Standard yield must have both quantity and unit") } // TODO: Localize
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, inlineError = null) }
            try {
                if (recipeId == null) {
                    val restaurant = restaurantRepository.getRestaurant()!!
                    val newId = preparationRecipeRepository.createDraft(
                        CreatePreparationRecipeCommand(
                            restaurantId = restaurant.id,
                            outputIngredientId = state.selectedOutputIngredient.id,
                            name = state.recipeName,
                            standardYieldQuantity = quantity,
                            yieldUnitOptionId = state.selectedYieldUnitOptionId,
                            notes = state.notes
                        )
                    )
                    _events.send(PreparationRecipeEditorEvent.Created(newId))
                } else {
                    preparationRecipeRepository.updateDraft(
                        UpdatePreparationRecipeCommand(
                            recipeId = recipeId,
                            name = state.recipeName,
                            standardYieldQuantity = quantity,
                            yieldUnitOptionId = state.selectedYieldUnitOptionId,
                            notes = state.notes
                        )
                    )
                    _events.send(PreparationRecipeEditorEvent.Saved)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.message ?: "Save failed") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onRemoveComponent(componentId: PreparationRecipeComponentId) {
        val rId = recipeId ?: return
        viewModelScope.launch {
            try {
                preparationRecipeRepository.removeComponent(rId, componentId)
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.message) }
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
            _uiState.update { it.copy(isReordering = true) }
            try {
                preparationRecipeRepository.reorderComponents(rId, mutableComponents.map { it.id })
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.message) }
            } finally {
                _uiState.update { it.copy(isReordering = false) }
            }
        }
    }
}
