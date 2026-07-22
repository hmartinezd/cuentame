package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.UpdateRestaurantProfileUseCase
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val restaurantRepository: RestaurantRepository,
    private val updateRestaurantProfileUseCase: UpdateRestaurantProfileUseCase
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error = _error.asStateFlow()

    val preferences: StateFlow<AppPreferences> = preferencesRepository.observePreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferences.DEFAULT
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColorEnabled(enabled)
        }
    }

    fun setAppLocaleTag(tag: String) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                // Update DataStore first
                preferencesRepository.setAppLocaleTag(tag)
                
                // Sync with restaurant if it exists
                restaurantRepository.getRestaurant()?.let {
                    updateRestaurantProfileUseCase(it.copy(localeTag = tag))
                }
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
