package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class IngredientListUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategoryId: IngredientCategoryId? = null,
    val showArchived: Boolean = false,
    val ingredients: List<Ingredient> = emptyList(),
    val categories: List<IngredientCategory> = emptyList(),
    val error: Throwable? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class IngredientListViewModel @Inject constructor(
    observeIngredientsUseCase: ObserveIngredientsUseCase,
    observeIngredientCategoriesUseCase: ObserveIngredientCategoriesUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<IngredientCategoryId?>(null)
    private val _showArchived = MutableStateFlow(false)

    val uiState: StateFlow<IngredientListUiState> = combine(
        observeIngredientsUseCase(),
        observeIngredientCategoriesUseCase(),
        _searchQuery.debounce(300),
        _selectedCategoryId,
        _showArchived
    ) { ingredients, categories, query, categoryId, showArchived ->
        val filtered = ingredients.filter { ingredient ->
            val matchesQuery = ingredient.name.contains(query, ignoreCase = true)
            val matchesCategory = categoryId == null || ingredient.categoryId == categoryId
            val matchesArchived = showArchived || ingredient.isActive
            matchesQuery && matchesCategory && matchesArchived
        }
        
        IngredientListUiState(
            isLoading = false,
            searchQuery = _searchQuery.value, // Use raw value for instant UI update if needed
            selectedCategoryId = categoryId,
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

    fun onCategorySelected(categoryId: IngredientCategoryId?) {
        _selectedCategoryId.value = categoryId
    }

    fun onShowArchivedToggled(show: Boolean) {
        _showArchived.value = show
    }
}
