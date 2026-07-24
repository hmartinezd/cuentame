package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.WasteDetails
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.usecase.DeleteWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.ObserveWasteEventDetailsUseCase
import com.miara.cuentame.core.domain.usecase.PostWasteEventUseCase
import com.miara.cuentame.core.domain.usecase.VoidWasteEventUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WasteDetailScreenState {
    data object Loading : WasteDetailScreenState
    data object Ready : WasteDetailScreenState
    data object NotFound : WasteDetailScreenState
    data object InvalidRoute : WasteDetailScreenState
    data object OwnershipMismatch : WasteDetailScreenState
    data class Error(val throwable: Throwable) : WasteDetailScreenState
}

data class WasteDetailUiState(
    val screenState: WasteDetailScreenState = WasteDetailScreenState.Loading,
    val isDeleting: Boolean = false,
    val isPosting: Boolean = false,
    val isVoiding: Boolean = false,
    val details: WasteDetails? = null,
    val currencyCode: String = "USD",
    val error: Throwable? = null
)

sealed interface WasteDetailEvent {
    data object Deleted : WasteDetailEvent
    data object Posted : WasteDetailEvent
    data object Voided : WasteDetailEvent
}

@HiltViewModel
class WasteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wasteRepository: WasteRepository,
    private val restaurantRepository: RestaurantRepository,
    private val observeWasteEventDetailsUseCase: ObserveWasteEventDetailsUseCase,
    private val deleteWasteDraftUseCase: DeleteWasteDraftUseCase,
    private val postWasteEventUseCase: PostWasteEventUseCase,
    private val voidWasteEventUseCase: VoidWasteEventUseCase
) : ViewModel() {

    private val wasteEventIdStr: String? = savedStateHandle["wasteEventId"]
    private val wasteEventId = wasteEventIdStr?.let { WasteEventId(it) }

    private val _isDeleting = MutableStateFlow(false)
    private val _isPosting = MutableStateFlow(false)
    private val _isVoiding = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _events = Channel<WasteDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<WasteDetailUiState> = combine(
        restaurantRepository.observeRestaurant(),
        if (wasteEventId != null) observeWasteEventDetailsUseCase(wasteEventId) else kotlinx.coroutines.flow.flowOf(null),
        _isDeleting,
        _isPosting,
        _isVoiding,
        _error,
        _hasLoadedOnce
    ) { args ->
        val restaurant = args[0] as com.miara.cuentame.core.model.restaurant.Restaurant?
        val details = args[1] as WasteDetails?
        val isDeleting = args[2] as Boolean
        val isPosting = args[3] as Boolean
        val isVoiding = args[4] as Boolean
        val error = args[5] as Throwable?
        val hasLoadedOnce = args[6] as Boolean

        val screenState = when {
            wasteEventId == null || wasteEventIdStr.isNullOrBlank() -> WasteDetailScreenState.InvalidRoute
            !hasLoadedOnce && error == null -> WasteDetailScreenState.Loading
            error != null && details == null -> WasteDetailScreenState.Error(error)
            details == null -> WasteDetailScreenState.NotFound
            restaurant == null || details.event.restaurantId != restaurant.id -> WasteDetailScreenState.OwnershipMismatch
            else -> WasteDetailScreenState.Ready
        }

        WasteDetailUiState(
            screenState = screenState,
            isDeleting = isDeleting,
            isPosting = isPosting,
            isVoiding = isVoiding,
            details = if (screenState == WasteDetailScreenState.Ready) details else null,
            currencyCode = restaurant?.currencyCode ?: "USD",
            error = error
        )
    }.onEach { if (it.details != null) _hasLoadedOnce.value = true }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WasteDetailUiState()
    )

    fun onDelete() {
        val cid = wasteEventId ?: return
        if (uiState.value.screenState != WasteDetailScreenState.Ready) return
        if (_isDeleting.value) return
        
        _isDeleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                deleteWasteDraftUseCase(cid)
                _events.send(WasteDetailEvent.Deleted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun onPost() {
        val cid = wasteEventId ?: return
        if (uiState.value.screenState != WasteDetailScreenState.Ready) return
        if (_isPosting.value) return
        
        _isPosting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                postWasteEventUseCase(cid)
                _events.send(WasteDetailEvent.Posted)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isPosting.value = false
            }
        }
    }

    fun onVoid() {
        val cid = wasteEventId ?: return
        if (uiState.value.screenState != WasteDetailScreenState.Ready) return
        if (_isVoiding.value) return
        
        _isVoiding.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                voidWasteEventUseCase(cid)
                _events.send(WasteDetailEvent.Voided)
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isVoiding.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
