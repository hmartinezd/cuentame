package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.usecase.locale.LocaleUpdateResult
import com.miara.cuentame.core.domain.usecase.locale.UpdateAppLocaleUseCase
import com.miara.cuentame.core.model.locale.SupportedAppLocale
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
    private val updateAppLocaleUseCase: UpdateAppLocaleUseCase
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
            try {
                preferencesRepository.setThemeMode(mode)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDynamicColorEnabled(enabled)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun setMenuManagementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try { preferencesRepository.setMenuManagementEnabled(enabled) }
            catch (e: Exception) { _error.value = e }
        }
    }

    fun setAppLocaleTag(tag: String) {
        if (_isSaving.value) return
        val locale = SupportedAppLocale.fromLanguageTag(tag) ?: return

        _isSaving.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                when (val result = updateAppLocaleUseCase(locale)) {
                    is LocaleUpdateResult.Success -> {
                        _isSaving.value = false
                    }
                    is LocaleUpdateResult.Error.RestaurantNotFound -> {
                        _isSaving.value = false
                        _error.value = IllegalStateException("Restaurant unavailable")
                    }
                    is LocaleUpdateResult.Error.RoomUpdateFailed -> {
                        _isSaving.value = false
                        _error.value = result.cause
                    }
                    is LocaleUpdateResult.Error.PreferenceUpdateFailed -> {
                        _isSaving.value = false
                        _error.value = result.cause
                    }
                }
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
