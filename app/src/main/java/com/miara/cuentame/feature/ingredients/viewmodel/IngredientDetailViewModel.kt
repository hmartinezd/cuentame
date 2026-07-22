package com.miara.cuentame.feature.ingredients.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.ArchiveIngredientUseCase
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IngredientDetailUiState(
    val isLoading: Boolean = true,
    val ingredient: Ingredient? = null,
    val options: List<IngredientUnitOption> = emptyList(),
    val error: Throwable? = null
)

sealed interface IngredientDetailEvent {
    data object ArchiveSuccess : IngredientDetailEvent
}

@HiltViewModel
class IngredientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getIngredientDetailUseCase: GetIngredientDetailUseCase,
    private val observeIngredientUnitOptionsUseCase: ObserveIngredientUnitOptionsUseCase,
    private val archiveIngredientUseCase: ArchiveIngredientUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val ingredientId: String = checkNotNull(savedStateHandle["ingredientId"])
    
    private val _events = Channel<IngredientDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<IngredientDetailUiState> = observeIngredientUnitOptionsUseCase(IngredientId(ingredientId))
        .map { options ->
            val ingredient = getIngredientDetailUseCase(IngredientId(ingredientId))
            IngredientDetailUiState(
                isLoading = false,
                ingredient = ingredient,
                options = options
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IngredientDetailUiState()
        )

    fun onArchiveIngredient() {
        viewModelScope.launch {
            try {
                archiveIngredientUseCase(IngredientId(ingredientId), timeProvider.now())
                _events.send(IngredientDetailEvent.ArchiveSuccess)
            } catch (e: Exception) {
                // TODO
            }
        }
    }
}
