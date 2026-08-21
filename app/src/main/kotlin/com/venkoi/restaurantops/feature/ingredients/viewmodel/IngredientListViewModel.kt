package com.venkoi.restaurantops.feature.ingredients.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.venkoi.restaurantops.core.domain.usecase.ObserveIngredientsUseCase
import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
import com.venkoi.restaurantops.core.domain.service.StarterCatalogSeeder
import com.venkoi.restaurantops.core.domain.service.StarterCatalogSeedResult
import com.venkoi.restaurantops.core.domain.service.StarterCatalogSeedFailure
import com.venkoi.restaurantops.core.model.catalog.CubanFoodiesStarterCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val hasAnyIngredients: Boolean = false,
    val ingredients: List<Ingredient> = emptyList(),
    val categories: List<IngredientCategory> = emptyList(),
    val sampleCatalogResult: StarterCatalogSeedResult? = null,
    val isAddingSampleCatalog: Boolean = false,
    val error: Throwable? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class IngredientListViewModel @Inject constructor(
    private val observeIngredientsUseCase: ObserveIngredientsUseCase,
    private val observeIngredientCategoriesUseCase: ObserveIngredientCategoriesUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val starterCatalogSeeder: StarterCatalogSeeder
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<IngredientCategoryFilter>(IngredientCategoryFilter.All)
    private val _showArchived = MutableStateFlow(false)
    private val _sampleCatalogResult = MutableStateFlow<StarterCatalogSeedResult?>(null)
    private val _isAddingSampleCatalog = MutableStateFlow(false)

    private val restaurantIdFlow = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .map { it.id }

    private val filteredUiState: StateFlow<IngredientListUiState> = combine(
        combine(
            combine(restaurantIdFlow, _showArchived) { rid, archived -> rid to archived }
                .flatMapLatest { (rid, archived) -> observeIngredientsUseCase(rid, archived) },
            restaurantIdFlow.flatMapLatest { rid -> observeIngredientsUseCase(rid, includeArchived = true) }
        ) { visibleIngredients, catalogIngredients -> visibleIngredients to catalogIngredients.isNotEmpty() },
        observeIngredientCategoriesUseCase(),
        _searchQuery.debounce(300),
        _categoryFilter,
        _showArchived
    ) { (ingredients, hasAnyIngredients), categories, query, categoryFilter, showArchived ->
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
            searchQuery = query,
            categoryFilter = categoryFilter,
            showArchived = showArchived,
            hasAnyIngredients = hasAnyIngredients,
            ingredients = filtered,
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngredientListUiState()
    )

    val uiState: StateFlow<IngredientListUiState> = combine(
        filteredUiState,
        _searchQuery,
        _sampleCatalogResult,
        _isAddingSampleCatalog
    ) { filteredState, rawQuery, sampleResult, addingSample ->
        filteredState.copy(searchQuery = rawQuery, sampleCatalogResult = sampleResult, isAddingSampleCatalog = addingSample)
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

    fun addSampleCatalog() = viewModelScope.launch {
        if (_isAddingSampleCatalog.value) return@launch
        _isAddingSampleCatalog.value = true
        try {
            val restaurant = restaurantRepository.getRestaurant() ?: return@launch
            _sampleCatalogResult.value = starterCatalogSeeder.seedNewRestaurant(restaurant.id.value, CubanFoodiesStarterCatalog.definition)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _sampleCatalogResult.value = StarterCatalogSeedResult.Failure(
                StarterCatalogSeedFailure.DatabaseError(e)
            )
        } finally {
            _isAddingSampleCatalog.value = false
        }
    }

    fun clearSampleCatalogResult() { _sampleCatalogResult.value = null }
}
