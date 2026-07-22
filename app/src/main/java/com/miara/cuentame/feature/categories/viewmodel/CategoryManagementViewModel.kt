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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManagementUiState(
    val categories: List<IngredientCategory> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: Throwable? = null
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

    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        observeIngredientCategoriesUseCase(),
        _isSaving,
        _error
    ) { categories, isSaving, error ->
        CategoryManagementUiState(
            categories = categories,
            isLoading = false,
            isSaving = isSaving,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryManagementUiState()
    )

    fun onAddCategory(name: String) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
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
                _isSaving.value = false
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onUpdateCategory(category: IngredientCategory) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                updateIngredientCategoryUseCase(category.copy(updatedAt = timeProvider.now()))
                _isSaving.value = false
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onArchiveCategory(id: IngredientCategoryId) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                archiveIngredientCategoryUseCase(id, timeProvider.now())
                _isSaving.value = false
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun onMoveUp(index: Int) {
        if (_isSaving.value || index <= 0) return
        val list = uiState.value.categories.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        executeReorder(list)
    }

    fun onMoveDown(index: Int) {
        if (_isSaving.value || index >= uiState.value.categories.size - 1) return
        val list = uiState.value.categories.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        executeReorder(list)
    }

    private fun executeReorder(newList: List<IngredientCategory>) {
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                reorderIngredientCategoriesUseCase(newList.map { it.id })
                _isSaving.value = false
            } catch (e: Exception) {
                _isSaving.value = false
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
