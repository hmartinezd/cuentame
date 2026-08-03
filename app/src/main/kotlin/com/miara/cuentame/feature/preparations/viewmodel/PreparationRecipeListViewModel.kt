package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
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
    val error: Throwable? = null
)

@HiltViewModel
class PreparationRecipeListViewModel @Inject constructor(
    private val preparationRecipeRepository: PreparationRecipeRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedStatus = MutableStateFlow<PreparationRecipeStatus?>(null)
    private val _includeArchived = MutableStateFlow(false)
    private val _retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _recipes = combine(
        restaurantRepository.observeRestaurant(),
        _includeArchived,
        _retryTrigger
    ) { restaurant, includeArchived, _ ->
        restaurant to includeArchived
    }.flatMapLatest { (restaurant, includeArchived) ->
        if (restaurant == null) flowOf(emptyList())
        else preparationRecipeRepository.observeRecipes(restaurant.id, includeArchived)
    }

    val uiState: StateFlow<PreparationRecipeListUiState> = combine(
        _recipes,
        _searchQuery,
        _selectedStatus,
        _includeArchived
    ) { recipes, query, status, includeArchived ->
        val filteredRecipes = recipes.filter { recipe ->
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
            recipes = filteredRecipes
        )
    }.catch { e ->
        emit(PreparationRecipeListUiState(isLoading = false, error = e))
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
    }

    fun onRetry() {
        _retryTrigger.value += 1
    }
}
