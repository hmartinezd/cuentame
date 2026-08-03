package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed interface PreparationRecipeComponentEvent {
    data object Saved : PreparationRecipeComponentEvent
}

data class PreparationRecipeComponentUiState(
    val isLoading: Boolean = true,
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
    val inlineError: String? = null
)

@HiltViewModel
class PreparationRecipeComponentViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId = PreparationRecipeId(savedStateHandle.get<String>("recipeId")!!)
    private val componentId = savedStateHandle.get<String>("componentId")?.let { PreparationRecipeComponentId(it) }

    private val _uiState = MutableStateFlow(PreparationRecipeComponentUiState())
    val uiState: StateFlow<PreparationRecipeComponentUiState> = _uiState.asStateFlow()

    private val _events = Channel<PreparationRecipeComponentEvent>()
    val events = _events.receiveAsFlow()

    private var isInitialized = false

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val restaurant = restaurantRepository.getRestaurant() ?: return@launch
            val recipe = preparationRecipeRepository.getRecipe(recipeId) ?: return@launch
            val ingredients = ingredientRepository.getIngredients(restaurant.id, includeArchived = false)

            val usedIngredientIds = recipe.components
                .filter { if (componentId != null) it.id != componentId else true }
                .map { it.componentIngredientId }
                .toSet()

            val availableIngredients = ingredients.filter { 
                it.id != recipe.outputIngredientId && it.id !in usedIngredientIds
            }

            if (!isInitialized) {
                val existingComp = if (componentId != null) recipe.components.find { it.id == componentId } else null
                if (existingComp != null) {
                    val unitOptions = ingredientRepository.getUnitOptions(existingComp.componentIngredientId, includeArchived = false)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
                            isLoading = false,
                            recipe = recipe,
                            availableIngredients = availableIngredients
                        )
                    }
                }
                isInitialized = true
            } else {
                _uiState.update {
                    it.copy(
                        recipe = recipe,
                        availableIngredients = availableIngredients
                    )
                }
            }
        }
    }

    fun onIngredientSelected(ingredient: Ingredient) {
        viewModelScope.launch {
            val unitOptions = ingredientRepository.getUnitOptions(ingredient.id, includeArchived = false)
            _uiState.update {
                it.copy(
                    selectedIngredient = ingredient,
                    availableUnitOptions = unitOptions,
                    selectedUnitOptionId = if (it.selectedUnitOptionId !in unitOptions.map { o -> o.id }) null else it.selectedUnitOptionId
                )
            }
        }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity, quantityError = false) }
    }

    fun onUnitOptionSelected(option: IngredientUnitOption) {
        _uiState.update { it.copy(selectedUnitOptionId = option.id) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.selectedIngredient == null || state.selectedUnitOptionId == null) return

        val parsedQuantity = try {
            BigDecimal(state.quantity)
        } catch (e: Exception) {
            _uiState.update { it.copy(quantityError = true) }
            return
        }

        if (parsedQuantity <= BigDecimal.ZERO) {
            _uiState.update { it.copy(quantityError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, inlineError = null) }
            try {
                preparationRecipeRepository.saveComponent(
                    SavePreparationRecipeComponentCommand(
                        recipeId = recipeId,
                        componentId = componentId,
                        componentIngredientId = state.selectedIngredient.id,
                        unitOptionId = state.selectedUnitOptionId,
                        quantityEntered = parsedQuantity,
                        sortOrder = state.recipe?.components?.size ?: 0, // Repository will handle reordering/clamping
                        notes = state.notes.ifBlank { null }
                    )
                )
                _events.send(PreparationRecipeComponentEvent.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(inlineError = e.message ?: "Save failed") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
