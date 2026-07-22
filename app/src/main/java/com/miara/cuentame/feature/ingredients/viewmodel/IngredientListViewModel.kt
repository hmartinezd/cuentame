package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface IngredientCategoryFilter {
    data object All : IngredientCategoryFilter
    data object Uncategorized : IngredientCategoryFilter
    data class Category(val id: IngredientCategoryId) : IngredientCategoryFilter
}

data class IngredientListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val categoryFilter: IngredientCategoryFilter = IngredientCategoryFilter.All,
    val showArchived: Boolean = false,
    val ingredients: List<Ingredient> = emptyList(),
    val categories: List<IngredientCategory> = emptyList(),
    val error: Throwable? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class IngredientListViewModel @Inject constructor(
    private val observeIngredientsUseCase: ObserveIngredientsUseCase,
    private val observeIngredientCategoriesUseCase: ObserveIngredientCategoriesUseCase,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<IngredientCategoryFilter>(IngredientCategoryFilter.All)
    private val _showArchived = MutableStateFlow(false)

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    val uiState: StateFlow<IngredientListUiState> = combine(
        combine(restaurantIdFlow, _showArchived) { rid, archived -> rid to archived }
            .flatMapLatest { (rid, archived) -> observeIngredientsUseCase(rid, archived) },
        observeIngredientCategoriesUseCase(),
        _searchQuery.debounce(300),
        _categoryFilter,
        _showArchived
    ) { ingredients, categories, query, categoryFilter, showArchived ->
        val normalizedQuery = query.normalizeName()
        val filtered = ingredients.filter { ingredient ->
            val matchesQuery = normalizedQuery.isEmpty() || ingredient.normalizedName.contains(normalizedQuery)
            val matchesCategory = when (categoryFilter) {
                IngredientCategoryFilter.All -> true
                IngredientCategoryFilter.Uncategorized -> ingredient.categoryId == null
                is IngredientCategoryFilter.Category -> ingredient.categoryId == categoryFilter.id
            }
            matchesQuery && matchesCategory
        }
        
        IngredientListUiState(
            isLoading = false,
            searchQuery = _searchQuery.value,
            categoryFilter = categoryFilter,
            showArchived = showArchived,
            ingredients = filtered,
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngredientListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategoryFilterChanged(filter: IngredientCategoryFilter) {
        _categoryFilter.value = filter
    }

    fun onShowArchivedToggled(show: Boolean) {
        _showArchived.value = show
    }
}
