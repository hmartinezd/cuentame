package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.domain.repository.PreparationCostRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.ingredient.PreparationRecipeCostSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreparationRecipeListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedStatus: PreparationRecipeStatus? = null,
    val includeArchived: Boolean = false,
    val recipes: List<PreparationRecipeSummary> = emptyList(),
    val costs: Map<com.miara.cuentame.core.common.ids.PreparationRecipeId, PreparationRecipeCostSummary> = emptyMap(),
    val error: Throwable? = null
)

sealed interface RecipeListLoadResult {
    data object Loading : RecipeListLoadResult
    data class Success(val recipes: List<PreparationRecipeSummary>, val costs: Map<com.miara.cuentame.core.common.ids.PreparationRecipeId, PreparationRecipeCostSummary>) : RecipeListLoadResult
    data class Failure(val error: Throwable) : RecipeListLoadResult
}

class RestaurantNotConfiguredException : Exception("Restaurant not configured")

@HiltViewModel
class PreparationRecipeListViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val restaurantRepository: RestaurantRepository,
    private val preparationCostRepository: PreparationCostRepository = EmptyListPreparationCostRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedStatus = MutableStateFlow<PreparationRecipeStatus?>(null)
    private val _includeArchived = MutableStateFlow(false)
    private val _retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _loadResult = combine(
        restaurantRepository.observeRestaurant(),
        _includeArchived,
        _retryTrigger
    ) { restaurant, includeArchived, retry ->
        Triple(restaurant, includeArchived, retry)
    }.flatMapLatest { (restaurant, includeArchived, _) ->
        if (restaurant == null) {
            flowOf(RecipeListLoadResult.Failure(RestaurantNotConfiguredException()))
        } else {
            combine(
                preparationRecipeRepository.observeRecipes(restaurant.id, includeArchived),
                preparationCostRepository.observeRecipeCostSummaries(restaurant.id)
            ) { recipes, costs -> recipes to costs }
                .map<Pair<List<PreparationRecipeSummary>, List<PreparationRecipeCostSummary>>, RecipeListLoadResult> { (recipes, costs) ->
                    RecipeListLoadResult.Success(recipes, costs.associateBy { it.recipeId })
                }
                .onStart {
                    emit(RecipeListLoadResult.Loading)
                }
                .catch { error ->
                    emit(RecipeListLoadResult.Failure(error))
                }
        }
    }

    val uiState: StateFlow<PreparationRecipeListUiState> = combine(
        _loadResult,
        _searchQuery,
        _selectedStatus,
        _includeArchived
    ) { result, query, status, includeArchived ->
        when (result) {
            is RecipeListLoadResult.Loading -> {
                PreparationRecipeListUiState(
                    isLoading = true,
                    searchQuery = query,
                    selectedStatus = status,
                    includeArchived = includeArchived
                )
            }
            is RecipeListLoadResult.Failure -> {
                PreparationRecipeListUiState(
                    isLoading = false,
                    searchQuery = query,
                    selectedStatus = status,
                    includeArchived = includeArchived,
                    error = result.error
                )
            }
            is RecipeListLoadResult.Success -> {
                val filteredRecipes = result.recipes.filter { recipe ->
                    val matchesQuery = if (query.isBlank()) true
                    else {
                        recipe.recipeName.contains(query, ignoreCase = true) ||
                                recipe.outputIngredientName.contains(query, ignoreCase = true)
                    }
                    val matchesStatus = if (status == null) true
                    else recipe.status == status

                    matchesQuery && matchesStatus
                }

                PreparationRecipeListUiState(
                    isLoading = false,
                    searchQuery = query,
                    selectedStatus = status,
                    includeArchived = includeArchived,
                    recipes = filteredRecipes,
                    costs = result.costs
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparationRecipeListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChanged(status: PreparationRecipeStatus?) {
        _selectedStatus.value = status
        if (status == PreparationRecipeStatus.ARCHIVED) {
            _includeArchived.value = true
        }
    }

    fun onIncludeArchivedToggled(includeArchived: Boolean) {
        _includeArchived.value = includeArchived
        if (!includeArchived && _selectedStatus.value == PreparationRecipeStatus.ARCHIVED) {
            _selectedStatus.value = null
        }
    }

    fun onRetry() {
        _retryTrigger.value += 1
    }
}

private object EmptyListPreparationCostRepository : PreparationCostRepository {
    override fun observeRecipeCost(recipeId: com.miara.cuentame.core.common.ids.PreparationRecipeId) = flowOf(null)
    override fun observeRecipeCostSummaries(restaurantId: com.miara.cuentame.core.common.ids.RestaurantId) = flowOf(emptyList<PreparationRecipeCostSummary>())
}
