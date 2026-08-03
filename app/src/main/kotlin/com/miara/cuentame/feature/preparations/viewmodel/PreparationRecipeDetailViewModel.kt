package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.database.repository.RecipeValidationException
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidationFailure
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreparationRecipeDetailUiState(
    val isLoading: Boolean = true,
    val isOperating: Boolean = false,
    val recipe: PreparationRecipe? = null,
    val outputIngredientName: String = "",
    val yieldUnitLabel: String = "",
    val componentNames: Map<PreparationRecipeComponentId, String> = emptyMap(),
    val componentUnitLabels: Map<PreparationRecipeComponentId, String> = emptyMap(),
    val error: Throwable? = null,
    val inlineError: Int? = null
)

@HiltViewModel
class PreparationRecipeDetailViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId = PreparationRecipeId(savedStateHandle.get<String>("recipeId")!!)

    private val _isOperating = MutableStateFlow(false)
    private val _inlineError = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<PreparationRecipeDetailUiState> = combine(
        preparationRecipeRepository.observeRecipe(recipeId),
        _isOperating,
        _inlineError
    ) { recipe, isOperating, inlineError ->
        if (recipe == null) {
            PreparationRecipeDetailUiState(isLoading = false, error = NoSuchElementException("Recipe not found"))
        } else {
            val outputIngredient = ingredientRepository.getById(recipe.outputIngredientId)
            val yieldUnitOption = recipe.yieldUnitOptionId?.let { ingredientRepository.getUnitOptions(recipe.outputIngredientId, true).find { o -> o.id == it } }
            
            val componentNames = mutableMapOf<PreparationRecipeComponentId, String>()
            val componentUnitLabels = mutableMapOf<PreparationRecipeComponentId, String>()
            
            for (comp in recipe.components) {
                componentNames[comp.id] = ingredientRepository.getById(comp.componentIngredientId)?.name ?: comp.componentIngredientId.value
                componentUnitLabels[comp.id] = ingredientRepository.getUnitOptions(comp.componentIngredientId, true).find { it.id == comp.unitOptionId }?.displayName ?: comp.unitOptionId.value
            }

            PreparationRecipeDetailUiState(
                isLoading = false,
                isOperating = isOperating,
                recipe = recipe,
                outputIngredientName = outputIngredient?.name ?: "",
                yieldUnitLabel = yieldUnitOption?.displayName ?: "",
                componentNames = componentNames,
                componentUnitLabels = componentUnitLabels,
                inlineError = inlineError
            )
        }
    }.catch { e ->
        emit(PreparationRecipeDetailUiState(isLoading = false, error = e))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparationRecipeDetailUiState()
    )

    fun onActivate() {
        performOperation { preparationRecipeRepository.activate(recipeId) }
    }

    fun onMoveToDraft() {
        performOperation { preparationRecipeRepository.moveToDraft(recipeId) }
    }

    fun onArchive() {
        performOperation { preparationRecipeRepository.archive(recipeId) }
    }

    fun onRestoreToDraft() {
        performOperation { preparationRecipeRepository.restoreToDraft(recipeId) }
    }

    private fun performOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            _isOperating.value = true
            _inlineError.value = null
            try {
                operation()
            } catch (e: RecipeValidationException) {
                _inlineError.value = mapValidationFailure(e.failures.first())
            } catch (e: Exception) {
                _inlineError.value = R.string.error_generic
            } finally {
                _isOperating.value = false
            }
        }
    }

    private fun mapValidationFailure(failure: PreparationRecipeValidationFailure): Int {
        return when (failure) {
            PreparationRecipeValidationFailure.AtLeastOneComponentRequired -> R.string.missing_components
            PreparationRecipeValidationFailure.YieldRequired,
            PreparationRecipeValidationFailure.YieldMustBePositive,
            PreparationRecipeValidationFailure.YieldUnitNotFound -> R.string.missing_yield
            PreparationRecipeValidationFailure.RecipeWouldCreateCycle -> R.string.circular_recipe_warning
            PreparationRecipeValidationFailure.OutputIngredientDeleted,
            PreparationRecipeValidationFailure.YieldUnitInactive,
            PreparationRecipeValidationFailure.ComponentIngredientDeleted,
            PreparationRecipeValidationFailure.ComponentUnitInactive -> R.string.error_inactive_reference
            PreparationRecipeValidationFailure.ComponentCannotBeOutput -> R.string.error_output_as_component
            PreparationRecipeValidationFailure.ComponentAlreadyExists -> R.string.error_duplicate_component
            PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput -> R.string.error_recipe_exists_for_output
            else -> R.string.activation_failed
        }
    }

    fun clearInlineError() {
        _inlineError.value = null
    }
}
