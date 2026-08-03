package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.feature.preparations.presentation.toPreparationRecipeUserMessage
import com.miara.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PreparationRecipeDetailEvent {
    data class NavigateToEditor(val recipeId: PreparationRecipeId) : PreparationRecipeDetailEvent
    data object LifecycleUpdated : PreparationRecipeDetailEvent
}

data class PreparationRecipeDetailUiState(
    val isLoading: Boolean = true,
    val isOperating: Boolean = false,
    val recipe: PreparationRecipe? = null,
    val outputIngredientName: String = "",
    val yieldUnitLabel: String = "",
    val componentNames: Map<PreparationRecipeComponentId, String> = emptyMap(),
    val componentUnitLabels: Map<PreparationRecipeComponentId, String> = emptyMap(),
    val error: Throwable? = null,
    val inlineError: UiMessage? = null
)

@HiltViewModel
class PreparationRecipeDetailViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId = PreparationRecipeId(savedStateHandle.get<String>("recipeId")!!)

    private val _isOperating = MutableStateFlow(false)
    private val _inlineError = MutableStateFlow<UiMessage?>(null)

    private val _events = Channel<PreparationRecipeDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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
        performOperation(navigateToEditor = true) { preparationRecipeRepository.moveToDraft(recipeId) }
    }

    fun onArchive() {
        performOperation { preparationRecipeRepository.archive(recipeId) }
    }

    fun onRestoreToDraft() {
        performOperation(navigateToEditor = true) { preparationRecipeRepository.restoreToDraft(recipeId) }
    }

    private fun performOperation(
        navigateToEditor: Boolean = false,
        operation: suspend () -> Unit
    ) {
        if (_isOperating.value) return
        _isOperating.value = true
        _inlineError.value = null
        viewModelScope.launch {
            try {
                operation()
                if (navigateToEditor) {
                    _events.send(PreparationRecipeDetailEvent.NavigateToEditor(recipeId))
                } else {
                    _events.send(PreparationRecipeDetailEvent.LifecycleUpdated)
                }
            } catch (e: Exception) {
                _inlineError.value = e.toPreparationRecipeUserMessage()
            } finally {
                _isOperating.value = false
            }
        }
    }

    fun clearInlineError() {
        _inlineError.value = null
    }
}
