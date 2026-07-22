package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.usecase.ObserveRestaurantProfileUseCase
import com.miara.cuentame.core.domain.usecase.UpdateRestaurantProfileUseCase
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestaurantSettingsUiState(
    val restaurant: Restaurant? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: Throwable? = null
)

@HiltViewModel
class RestaurantSettingsViewModel @Inject constructor(
    observeRestaurantProfileUseCase: ObserveRestaurantProfileUseCase,
    private val updateRestaurantProfileUseCase: UpdateRestaurantProfileUseCase,
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    val uiState: StateFlow<RestaurantSettingsUiState> = combine(
        observeRestaurantProfileUseCase(),
        _isSaving,
        _error
    ) { restaurant, isSaving, error ->
        RestaurantSettingsUiState(
            restaurant = restaurant,
            isLoading = restaurant == null,
            isSaving = isSaving,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestaurantSettingsUiState()
    )

    fun onUpdateRestaurant(name: String, currency: String, locale: String, onSuccess: () -> Unit) {
        val current = uiState.value.restaurant ?: return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val updated = current.copy(
                    name = name,
                    currencyCode = currency,
                    localeTag = locale
                )
                updateRestaurantProfileUseCase(updated)
                preferencesRepository.setAppLocaleTag(locale)
                _isSaving.value = false
                onSuccess()
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
