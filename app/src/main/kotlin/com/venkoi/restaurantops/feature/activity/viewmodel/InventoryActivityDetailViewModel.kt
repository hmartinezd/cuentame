package com.venkoi.restaurantops.feature.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.InventoryMovementId
import com.venkoi.restaurantops.core.domain.repository.InventoryActivityRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityItem
import com.venkoi.restaurantops.core.model.inventory.InventoryActivitySourceTarget
import com.venkoi.restaurantops.core.presentation.ui.UiMessage
import com.venkoi.restaurantops.feature.activity.logic.InventoryActivityTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InventoryActivityDetailScreenState {
    data object Loading : InventoryActivityDetailScreenState
    data object InvalidRoute : InventoryActivityDetailScreenState
    data object MovementNotFound : InventoryActivityDetailScreenState
    data class Ready(
        val item: InventoryActivityItem,
        val sourceTarget: InventoryActivitySourceTarget,
        val currencyCode: String,
        val localeTag: String
    ) : InventoryActivityDetailScreenState
    data class LoadError(val message: UiMessage) : InventoryActivityDetailScreenState
}

@HiltViewModel
class InventoryActivityDetailViewModel @Inject constructor(
    private val activityRepository: InventoryActivityRepository,
    private val restaurantRepository: RestaurantRepository,
    val textResolver: InventoryActivityTextResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movementId = savedStateHandle.get<String>("movementId")?.let { InventoryMovementId(it) }

    private val _uiState = MutableStateFlow<InventoryActivityDetailScreenState>(InventoryActivityDetailScreenState.Loading)
    val uiState: StateFlow<InventoryActivityDetailScreenState> = _uiState.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    init {
        loadDetail()
    }

    private fun loadDetail() {
        if (movementId == null) {
            _uiState.value = InventoryActivityDetailScreenState.InvalidRoute
            return
        }

        viewModelScope.launch {
            retryTrigger.collect {
                _uiState.value = InventoryActivityDetailScreenState.Loading
                try {
                    val restaurant = restaurantRepository.getRestaurant()
                    if (restaurant == null) {
                        _uiState.value = InventoryActivityDetailScreenState.LoadError(UiMessage.Resource(R.string.error_no_restaurant))
                        return@collect
                    }

                    val item = activityRepository.getActivityItem(restaurant.id, movementId)
                    if (item == null) {
                        _uiState.value = InventoryActivityDetailScreenState.MovementNotFound
                    } else {
                        _uiState.value = InventoryActivityDetailScreenState.Ready(
                            item = item,
                            sourceTarget = activityRepository.resolveSourceTarget(item),
                            currencyCode = restaurant.currencyCode,
                            localeTag = restaurant.localeTag
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = InventoryActivityDetailScreenState.LoadError(UiMessage.Resource(R.string.error_generic))
                }
            }
        }
    }

    fun onRetry() {
        retryTrigger.value += 1
    }
}
