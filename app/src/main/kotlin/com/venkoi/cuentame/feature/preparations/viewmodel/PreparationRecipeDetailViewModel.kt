package com.venkoi.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.PreparationRecipeComponentId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.domain.repository.IngredientRepository
import com.venkoi.cuentame.core.domain.repository.PreparationRecipeRepository
import com.venkoi.cuentame.core.domain.repository.PreparationCostRepository
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipe
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeCost
import com.venkoi.cuentame.feature.preparations.presentation.toPreparationRecipeUserMessage
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    data class Success(
        val recipe: PreparationRecipe,
        val outputIngredientName: String,
        val yieldUnitLabel: String,
        val componentNames: Map<PreparationRecipeComponentId, String>,
        val componentUnitLabels: Map<PreparationRecipeComponentId, String>,
        val currentCost: PreparationRecipeCost?
    ) : RecipeDetailLoadResult
    data object NotFound : RecipeDetailLoadResult
    data object InvalidRoute : RecipeDetailLoadResult
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
    val currentCost: PreparationRecipeCost? = null,
    val error: Throwable? = null,
    val inlineError: UiMessage? = null
)

@HiltViewModel
class PreparationRecipeDetailViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    savedStateHandle: SavedStateHandle,
    private val preparationCostRepository: PreparationCostRepository = EmptyPreparationCostRepository
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
            flowOf(RecipeDetailLoadResult.InvalidRoute)
        } else {
            combine(preparationRecipeRepository.observeRecipe(rId), preparationCostRepository.observeRecipeCost(rId)) { recipe, cost -> recipe to cost }
                .mapLatest { (recipe, cost) ->
                    if (recipe == null) return@mapLatest RecipeDetailLoadResult.NotFound

                    val outputIngredient = ingredientRepository.getById(recipe.outputIngredientId)
                        ?: throw IllegalStateException("Output ingredient not found")
                    
                    val yieldUnitLabel = recipe.yieldUnitOptionId?.let { unitId ->
                        ingredientRepository.getUnitOptions(recipe.outputIngredientId, true)
                            .find { it.id == unitId }?.displayName
                            ?: throw IllegalStateException("Yield unit not found")
                    } ?: ""

                    val componentNames = mutableMapOf<PreparationRecipeComponentId, String>()
                    val componentUnitLabels = mutableMapOf<PreparationRecipeComponentId, String>()

                    for (comp in recipe.components) {
                        componentNames[comp.id] = ingredientRepository.getById(comp.componentIngredientId)?.name
                            ?: throw IllegalStateException("Component ingredient not found")
                        componentUnitLabels[comp.id] = ingredientRepository.getUnitOptions(comp.componentIngredientId, true)
                            .find { it.id == comp.unitOptionId }?.displayName
                            ?: throw IllegalStateException("Component unit not found")
                    }

                    RecipeDetailLoadResult.Success(
                        recipe = recipe,
                        outputIngredientName = outputIngredient.name,
                        yieldUnitLabel = yieldUnitLabel,
                        componentNames = componentNames,
                        componentUnitLabels = componentUnitLabels,
                        currentCost = cost
                    )
                }
                .onStart { emit(RecipeDetailLoadResult.Loading) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(RecipeDetailLoadResult.Failure(e))
                }
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
            is RecipeDetailLoadResult.InvalidRoute -> {
                PreparationRecipeDetailUiState(loadState = PreparationScreenLoadState.InvalidRoute, isOperating = isOperating)
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
                PreparationRecipeDetailUiState(
                    loadState = PreparationScreenLoadState.EditReady,
                    isOperating = isOperating,
                    recipe = result.recipe,
                    outputIngredientName = result.outputIngredientName,
                    yieldUnitLabel = result.yieldUnitLabel,
                    componentNames = result.componentNames,
                    componentUnitLabels = result.componentUnitLabels,
                    currentCost = result.currentCost,
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
            } catch (cancellation: CancellationException) {
                throw cancellation
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

private object EmptyPreparationCostRepository : PreparationCostRepository {
    override fun observeRecipeCost(recipeId: PreparationRecipeId) = flowOf(null)
    override fun observeRecipeCostSummaries(restaurantId: com.venkoi.cuentame.core.common.ids.RestaurantId) = flowOf(emptyList<com.venkoi.cuentame.core.model.ingredient.PreparationRecipeCostSummary>())
}
