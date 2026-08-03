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

sealed interface RecipeDetailLoadResult {
    data object Loading : RecipeDetailLoadResult
    data class Success(val recipe: PreparationRecipe) : RecipeDetailLoadResult
    data object NotFound : RecipeDetailLoadResult
    data class Failure(val error: Throwable) : RecipeDetailLoadResult
}

data class PreparationRecipeDetailUiState(
    val loadState: PreparationScreenLoadState = PreparationScreenLoadState.Loading,
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

    private val recipeId = savedStateHandle.get<String>("recipeId")
        ?.takeIf { it.isNotBlank() }
        ?.let { PreparationRecipeId(it) }

    private val _retryTrigger = MutableStateFlow(0)
    private val _isOperating = MutableStateFlow(false)
    private val _inlineError = MutableStateFlow<UiMessage?>(null)

    private val _events = Channel<PreparationRecipeDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _loadResult = _retryTrigger.flatMapLatest {
        val rId = recipeId
        if (rId == null) {
            flowOf(RecipeDetailLoadResult.NotFound)
        } else {
            preparationRecipeRepository.observeRecipe(rId)
                .map<PreparationRecipe?, RecipeDetailLoadResult> { recipe ->
                    if (recipe == null) RecipeDetailLoadResult.NotFound
                    else RecipeDetailLoadResult.Success(recipe)
                }
                .onStart { emit(RecipeDetailLoadResult.Loading) }
                .catch { e -> emit(RecipeDetailLoadResult.Failure(e)) }
        }
    }

    val uiState: StateFlow<PreparationRecipeDetailUiState> = combine(
        _loadResult,
        _isOperating,
        _inlineError
    ) { result, isOperating, inlineError ->
        when (result) {
            is RecipeDetailLoadResult.Loading -> {
                PreparationRecipeDetailUiState(loadState = PreparationScreenLoadState.Loading, isOperating = isOperating)
            }
            is RecipeDetailLoadResult.NotFound -> {
                PreparationRecipeDetailUiState(loadState = PreparationScreenLoadState.RecipeNotFound, isOperating = isOperating)
            }
            is RecipeDetailLoadResult.Failure -> {
                PreparationRecipeDetailUiState(
                    loadState = PreparationScreenLoadState.LoadError(result.error.toPreparationRecipeUserMessage()),
                    isOperating = isOperating,
                    error = result.error
                )
            }
            is RecipeDetailLoadResult.Success -> {
                val recipe = result.recipe
                val outputIngredient = try { ingredientRepository.getById(recipe.outputIngredientId) } catch (_: Exception) { null }
                val yieldUnitOption = recipe.yieldUnitOptionId?.let { 
                    try { ingredientRepository.getUnitOptions(recipe.outputIngredientId, true).find { o -> o.id == it } } catch (_: Exception) { null }
                }
                
                val componentNames = mutableMapOf<PreparationRecipeComponentId, String>()
                val componentUnitLabels = mutableMapOf<PreparationRecipeComponentId, String>()
                
                for (comp in recipe.components) {
                    componentNames[comp.id] = try { ingredientRepository.getById(comp.componentIngredientId)?.name ?: comp.componentIngredientId.value } catch (_: Exception) { comp.componentIngredientId.value }
                    componentUnitLabels[comp.id] = try { ingredientRepository.getUnitOptions(comp.componentIngredientId, true).find { it.id == comp.unitOptionId }?.displayName ?: comp.unitOptionId.value } catch (_: Exception) { comp.unitOptionId.value }
                }

                PreparationRecipeDetailUiState(
                    loadState = PreparationScreenLoadState.EditReady,
                    isOperating = isOperating,
                    recipe = recipe,
                    outputIngredientName = outputIngredient?.name ?: "",
                    yieldUnitLabel = yieldUnitOption?.displayName ?: "",
                    componentNames = componentNames,
                    componentUnitLabels = componentUnitLabels,
                    inlineError = inlineError
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparationRecipeDetailUiState()
    )

    fun onActivate() {
        val rId = recipeId ?: return
        performOperation { preparationRecipeRepository.activate(rId) }
    }

    fun onMoveToDraft() {
        val rId = recipeId ?: return
        performOperation(navigateToEditor = true) { preparationRecipeRepository.moveToDraft(rId) }
    }

    fun onArchive() {
        val rId = recipeId ?: return
        performOperation { preparationRecipeRepository.archive(rId) }
    }

    fun onRestoreToDraft() {
        val rId = recipeId ?: return
        performOperation(navigateToEditor = true) { preparationRecipeRepository.restoreToDraft(rId) }
    }

    fun onRetry() {
        _retryTrigger.value += 1
    }

    private fun performOperation(
        navigateToEditor: Boolean = false,
        operation: suspend () -> Unit
    ) {
        val rId = recipeId ?: return
        if (_isOperating.value) return
        _isOperating.value = true
        _inlineError.value = null
        viewModelScope.launch {
            try {
                operation()
                if (navigateToEditor) {
                    _events.send(PreparationRecipeDetailEvent.NavigateToEditor(rId))
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
