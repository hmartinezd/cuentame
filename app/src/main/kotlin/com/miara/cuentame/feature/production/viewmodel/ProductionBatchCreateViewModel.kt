package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.feature.production.presentation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

sealed interface ProductionBatchCreateEvent {
    data class Created(val batchId: ProductionBatchId) : ProductionBatchCreateEvent
}

data class ProductionBatchCreateUiState(
    val screenState: ProductionBatchScreenState = ProductionBatchScreenState.Loading,
    val isCreating: Boolean = false,
    
    val availableRecipes: List<PreparationRecipeSummary> = emptyList(),
    val availableAreas: List<InventoryArea> = emptyList(),
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    
    // Form state
    val selectedRecipeSummary: PreparationRecipeSummary? = null,
    val selectedRecipe: PreparationRecipe? = null,
    val multiplier: String = "1",
    val selectedAreaId: InventoryAreaId? = null,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val actualOutputQuantity: String = "",
    val effectiveAt: Instant = Instant.now(),
    val notes: String = "",
    
    // Calculated values
    val expectedOutputEntered: BigDecimal? = null,
    
    // Validation
    val multiplierError: Boolean = false,
    val actualOutputError: Boolean = false,
    val inlineError: UiMessage? = null
)

@HiltViewModel
class ProductionBatchCreateViewModel @Inject constructor(
    private val productionBatchRepository: ProductionBatchRepository,
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val inventoryAreaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialRecipeId = savedStateHandle.get<String>("recipeId")
        ?.takeIf { it.isNotBlank() }
        ?.let { PreparationRecipeId(it) }

    private var initialRouteResolved = false

    private val _uiState = MutableStateFlow(ProductionBatchCreateUiState(effectiveAt = timeProvider.now()))
    val uiState: StateFlow<ProductionBatchCreateUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProductionBatchCreateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val retryTrigger = MutableStateFlow(0)
    private val recipeDetailsRequests = MutableSharedFlow<PreparationRecipeId>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        observeInitialData()
        observeRecipeDetails()
    }

    private fun observeInitialData() {
        viewModelScope.launch {
            retryTrigger.collectLatest {
                _uiState.update { it.copy(screenState = ProductionBatchScreenState.Loading) }
                try {
                    val restaurant = restaurantRepository.getRestaurant()
                    if (restaurant == null) {
                        _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_no_restaurant))) }
                        return@collectLatest
                    }

                    val recipesFlow = preparationRecipeRepository.observeRecipes(restaurant.id, includeArchived = false)
                    val areasFlow = inventoryAreaRepository.observeActiveAreas()

                    combine(recipesFlow, areasFlow) { recipes: List<PreparationRecipeSummary>, areas: List<InventoryArea> ->
                        recipes.filter { it.status == PreparationRecipeStatus.ACTIVE } to areas
                    }.collectLatest { (activeRecipes: List<PreparationRecipeSummary>, areas: List<InventoryArea>) ->
                        _uiState.update {
                            it.copy(
                                screenState = ProductionBatchScreenState.Ready,
                                availableRecipes = activeRecipes,
                                availableAreas = areas
                            )
                        }

                        if (initialRecipeId != null && !initialRouteResolved) {
                            initialRouteResolved = true
                            val preselected = activeRecipes.find { it.id == initialRecipeId }
                            if (preselected != null) {
                                onRecipeSelected(preselected)
                            } else {
                                val fullRecipe = preparationRecipeRepository.getRecipe(initialRecipeId)
                                when {
                                    fullRecipe == null -> {
                                        _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_recipe_not_found)) }
                                    }
                                    fullRecipe.status != PreparationRecipeStatus.ACTIVE -> {
                                        _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_recipe_not_active)) }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(screenState = ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic))) }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecipeDetails() {
        viewModelScope.launch {
            recipeDetailsRequests.collectLatest { recipeId ->
                try {
                    val recipe = preparationRecipeRepository.getRecipe(recipeId)
                    if (recipe == null || recipe.status != PreparationRecipeStatus.ACTIVE) {
                        _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_recipe_not_active)) }
                        return@collectLatest
                    }

                    val unitOptions = ingredientRepository.getUnitOptions(recipe.outputIngredientId, includeArchived = false)
                    val outputIngredient = ingredientRepository.getById(recipe.outputIngredientId)
                    
                    _uiState.update { state ->
                        state.copy(
                            selectedRecipe = recipe,
                            availableUnitOptions = unitOptions,
                            selectedUnitOptionId = recipe.yieldUnitOptionId ?: unitOptions.find { it.isBase }?.id,
                            selectedAreaId = outputIngredient?.defaultAreaId?.takeIf { areaId -> 
                                state.availableAreas.any { it.id == areaId }
                            }
                        )
                    }
                    calculateExpectedOutput()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_generic)) }
                }
            }
        }
    }

    fun onRecipeSelected(recipeSummary: PreparationRecipeSummary) {
        _uiState.update { 
            it.copy(
                selectedRecipeSummary = recipeSummary, 
                selectedRecipe = null, 
                inlineError = null,
                availableUnitOptions = emptyList(),
                selectedUnitOptionId = null,
                selectedAreaId = null,
                actualOutputQuantity = "",
                expectedOutputEntered = null
            ) 
        }
        recipeDetailsRequests.tryEmit(recipeSummary.id)
    }

    fun onMultiplierChanged(multiplier: String) {
        _uiState.update { it.copy(multiplier = multiplier, multiplierError = false, inlineError = null) }
        calculateExpectedOutput()
    }

    fun onAreaSelected(areaId: InventoryAreaId) {
        _uiState.update { it.copy(selectedAreaId = areaId, inlineError = null) }
    }

    fun onUnitOptionSelected(optionId: IngredientUnitOptionId) {
        _uiState.update { it.copy(selectedUnitOptionId = optionId, inlineError = null) }
    }

    fun onActualOutputChanged(quantity: String) {
        _uiState.update { it.copy(actualOutputQuantity = quantity, actualOutputError = false, inlineError = null) }
    }

    fun onEffectiveAtChanged(instant: Instant) {
        _uiState.update { it.copy(effectiveAt = instant, inlineError = null) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    private fun calculateExpectedOutput() {
        val state = _uiState.value
        val recipe = state.selectedRecipe ?: return
        val yield = recipe.standardYieldQuantity ?: return
        val multiplierVal = try {
            BigDecimal(state.multiplier)
        } catch (e: Exception) {
            null
        }

        if (multiplierVal != null && multiplierVal > BigDecimal.ZERO) {
            val expected = yield.multiply(multiplierVal, MathContext.DECIMAL128)
            _uiState.update { it.copy(expectedOutputEntered = expected) }
        } else {
            _uiState.update { it.copy(expectedOutputEntered = null) }
        }
    }

    fun onCreate() {
        val state = _uiState.value
        if (state.isCreating) return

        val recipe = state.selectedRecipe
        if (recipe == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_recipe_not_active)) }
            return
        }

        val multiplierVal = try {
            BigDecimal(state.multiplier)
        } catch (e: Exception) {
            _uiState.update { it.copy(multiplierError = true, inlineError = UiMessage.Resource(R.string.error_invalid_decimal)) }
            return
        }

        if (multiplierVal <= BigDecimal.ZERO) {
            _uiState.update { it.copy(multiplierError = true, inlineError = UiMessage.Resource(R.string.error_multiplier_positive)) }
            return
        }

        if (state.selectedAreaId == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_area_required)) }
            return
        }

        if (state.selectedUnitOptionId == null) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_unit_required)) }
            return
        }

        val actualOutputVal = if (state.actualOutputQuantity.isBlank()) null else {
            try {
                BigDecimal(state.actualOutputQuantity)
            } catch (e: Exception) {
                _uiState.update { it.copy(actualOutputError = true, inlineError = UiMessage.Resource(R.string.error_invalid_decimal)) }
                return
            }
        }

        if (actualOutputVal != null && actualOutputVal <= BigDecimal.ZERO) {
            _uiState.update { it.copy(actualOutputError = true, inlineError = UiMessage.Resource(R.string.error_quantity_positive)) }
            return
        }

        if (state.effectiveAt.isAfter(timeProvider.now())) {
            _uiState.update { it.copy(inlineError = UiMessage.Resource(R.string.error_future_effective_time)) }
            return
        }

        _uiState.update { it.copy(isCreating = true, inlineError = null) }
        viewModelScope.launch {
            try {
                val restaurant = restaurantRepository.getRestaurant() 
                if (restaurant == null) {
                    _uiState.update { it.copy(isCreating = false, inlineError = UiMessage.Resource(R.string.error_no_restaurant)) }
                    return@launch
                }
                
                val batchId = productionBatchRepository.createDraft(
                    CreateProductionBatchDraftCommand(
                        restaurantId = restaurant.id,
                        recipeId = recipe.id,
                        batchMultiplier = multiplierVal,
                        outputAreaId = state.selectedAreaId,
                        actualOutputQuantityEntered = actualOutputVal,
                        outputUnitOptionId = state.selectedUnitOptionId,
                        effectiveAt = state.effectiveAt,
                        notes = state.notes.trim().ifBlank { null }
                    )
                )
                _uiState.update { it.copy(isCreating = false) }
                _events.send(ProductionBatchCreateEvent.Created(batchId))
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isCreating = false) }
                throw e
            } catch (e: ProductionBatchValidationException) {
                _uiState.update { it.copy(isCreating = false, inlineError = e.failures.toUserMessage()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreating = false, inlineError = UiMessage.Resource(R.string.error_generic)) }
            }
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }
}
