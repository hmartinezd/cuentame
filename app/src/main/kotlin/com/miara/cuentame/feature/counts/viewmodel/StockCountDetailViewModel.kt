package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountDetails
import com.miara.cuentame.core.domain.repository.StockCountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockCountDetailUiState(
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val isCompleting: Boolean = false,
    val isVoiding: Boolean = false,
    val details: StockCountDetails? = null,
    val error: Throwable? = null
)

sealed interface StockCountDetailEvent {
    data object Deleted : StockCountDetailEvent
    data object Completed : StockCountDetailEvent
    data object Voided : StockCountDetailEvent
}

@HiltViewModel
class StockCountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockCountRepository
) : ViewModel() {

    private val countId = StockCountId(checkNotNull(savedStateHandle["countId"]))

    private val _isDeleting = MutableStateFlow(false)
    private val _isCompleting = MutableStateFlow(false)
    private val _isVoiding = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val _events = Channel<StockCountDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<StockCountDetailUiState> = combine(
        repository.observeCount(countId),
        _isDeleting,
        _isCompleting,
        _isVoiding,
        _error
    ) { details, deleting, completing, voiding, error ->
        StockCountDetailUiState(
            isLoading = details == null && error == null,
            isDeleting = deleting,
            isCompleting = completing,
            isVoiding = voiding,
            details = details,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockCountDetailUiState()
    )

    fun onDelete() {
        if (_isDeleting.value) return
        _isDeleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.deleteDraft(countId)
                _events.send(StockCountDetailEvent.Deleted)
            } catch (e: Exception) {
                _isDeleting.value = false
                _error.value = e
            }
        }
    }

    fun onComplete() {
        if (_isCompleting.value) return
        _isCompleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.completeCount(countId)
                _events.send(StockCountDetailEvent.Completed)
            } catch (e: Exception) {
                _isCompleting.value = false
                _error.value = e
            }
        }
    }

    fun onVoid() {
        if (_isVoiding.value) return
        _isVoiding.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repository.voidCount(countId)
                _events.send(StockCountDetailEvent.Voided)
            } catch (e: Exception) {
                _isVoiding.value = false
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
