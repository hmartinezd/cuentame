package com.miara.cuentame.feature.priceintelligence.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface PriceHistoryState {
    data object Loading : PriceHistoryState
    data class Ready(val history: IngredientPriceHistory) : PriceHistoryState
    data object Error : PriceHistoryState
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PriceHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PriceIntelligenceRepository
) : ViewModel() {
    private val ingredientId = IngredientId(requireNotNull(savedStateHandle["ingredientId"]))
    private val retry = MutableStateFlow(0)
    val state: StateFlow<PriceHistoryState> = retry.flatMapLatest {
        repository.observeIngredientPriceHistory(ingredientId)
            .map<IngredientPriceHistory, PriceHistoryState> { PriceHistoryState.Ready(it) }
            .onStart { emit(PriceHistoryState.Loading) }
            .catch { emit(PriceHistoryState.Error) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceHistoryState.Loading)
    fun retry() { retry.value++ }
}

sealed interface PriceAlertsState {
    data object Loading : PriceAlertsState
    data class Ready(val alerts: List<PriceIncreaseAlert>) : PriceAlertsState
    data object Error : PriceAlertsState
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PriceAlertsViewModel @Inject constructor(
    private val repository: PriceIntelligenceRepository
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val state: StateFlow<PriceAlertsState> = retry.flatMapLatest {
        repository.observeLargePriceIncreases()
            .map<List<PriceIncreaseAlert>, PriceAlertsState> { PriceAlertsState.Ready(it) }
            .onStart { emit(PriceAlertsState.Loading) }
            .catch { emit(PriceAlertsState.Error) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceAlertsState.Loading)
    fun retry() { retry.value++ }
}
