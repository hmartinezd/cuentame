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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestaurantSettingsUiState(
    val restaurant: Restaurant? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

@HiltViewModel
class RestaurantSettingsViewModel @Inject constructor(
    observeRestaurantProfileUseCase: ObserveRestaurantProfileUseCase,
    private val updateRestaurantProfileUseCase: UpdateRestaurantProfileUseCase,
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<RestaurantSettingsUiState> = observeRestaurantProfileUseCase()
        .map { RestaurantSettingsUiState(restaurant = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RestaurantSettingsUiState()
        )

    fun onUpdateRestaurant(name: String, currency: String, locale: String) {
        val current = uiState.value.restaurant ?: return
        _isSaving.value = true // Need to add internal state for saving
        viewModelScope.launch {
            val updated = current.copy(
                name = name,
                currencyCode = currency,
                localeTag = locale
            )
            updateRestaurantProfileUseCase(updated)
            preferencesRepository.setAppLocaleTag(locale)
            _isSaving.value = false
        }
    }
    
    private val _isSaving = MutableStateFlow(false)
}
