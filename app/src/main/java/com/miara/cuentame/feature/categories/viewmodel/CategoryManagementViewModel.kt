package com.miara.cuentame.feature.categories.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientCategoryUseCase
import com.miara.cuentame.core.domain.usecase.CreateIngredientCategoryUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.ReorderIngredientCategoriesUseCase
import com.miara.cuentame.core.domain.usecase.UpdateIngredientCategoryUseCase
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManagementUiState(
    val categories: List<IngredientCategory> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    observeIngredientCategoriesUseCase: ObserveIngredientCategoriesUseCase,
    private val createIngredientCategoryUseCase: CreateIngredientCategoryUseCase,
    private val updateIngredientCategoryUseCase: UpdateIngredientCategoryUseCase,
    private val archiveIngredientCategoryUseCase: ArchiveIngredientCategoryUseCase,
    private val reorderIngredientCategoriesUseCase: ReorderIngredientCategoriesUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : ViewModel() {

    val uiState: StateFlow<CategoryManagementUiState> = observeIngredientCategoriesUseCase()
        .map { CategoryManagementUiState(categories = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryManagementUiState()
        )

    fun onAddCategory(name: String) {
        viewModelScope.launch {
            val restaurant = restaurantRepository.getRestaurant() ?: return@launch
            val category = IngredientCategory(
                id = IngredientCategoryId(idGenerator.newId()),
                restaurantId = restaurant.id,
                name = name,
                normalizedName = "",
                sortOrder = uiState.value.categories.size,
                isActive = true,
                createdAt = timeProvider.now(),
                updatedAt = timeProvider.now()
            )
            createIngredientCategoryUseCase(category)
        }
    }

    fun onUpdateCategory(category: IngredientCategory) {
        viewModelScope.launch {
            updateIngredientCategoryUseCase(category.copy(updatedAt = timeProvider.now()))
        }
    }

    fun onArchiveCategory(id: IngredientCategoryId) {
        viewModelScope.launch {
            archiveIngredientCategoryUseCase(id, timeProvider.now())
        }
    }

    fun onMoveUp(index: Int) {
        if (index <= 0) return
        val list = uiState.value.categories.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        viewModelScope.launch {
            reorderIngredientCategoriesUseCase(list.map { it.id })
        }
    }

    fun onMoveDown(index: Int) {
        if (index >= uiState.value.categories.size - 1) return
        val list = uiState.value.categories.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        viewModelScope.launch {
            reorderIngredientCategoriesUseCase(list.map { it.id })
        }
    }
}
