package com.miara.cuentame.feature.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.inventory.InventoryActivityItem
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.presentation.ui.UiMessage
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
        val currencyCode: String
    ) : InventoryActivityDetailScreenState
    data class LoadError(val message: UiMessage) : InventoryActivityDetailScreenState
}

@HiltViewModel
class InventoryActivityDetailViewModel @Inject constructor(
    private val activityRepository: InventoryActivityRepository,
    private val restaurantRepository: RestaurantRepository,
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

                    val item = activityRepository.getActivityItem(movementId)
                    if (item == null) {
                        _uiState.value = InventoryActivityDetailScreenState.MovementNotFound
                    } else {
                        _uiState.value = InventoryActivityDetailScreenState.Ready(
                            item = item,
                            sourceTarget = activityRepository.resolveSourceTarget(item),
                            currencyCode = restaurant.currencyCode
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
