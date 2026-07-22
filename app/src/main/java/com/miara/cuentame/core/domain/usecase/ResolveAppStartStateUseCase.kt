package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

sealed interface AppStartState {
    data object Loading : AppStartState
    data object RequiresOnboarding : AppStartState
    data object Ready : AppStartState
}

class ResolveAppStartStateUseCase @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val setupRepository: LocalSetupRepository,
    private val restaurantRepository: RestaurantRepository
) {
    operator fun invoke(): Flow<AppStartState> = combine(
        preferencesRepository.observePreferences(),
        setupRepository.observeIsSetupComplete()
    ) { prefs, isDbComplete ->
        val isPrefComplete = prefs.onboardingCompleted

        when {
            isDbComplete && !isPrefComplete -> {
                // Repair DataStore
                preferencesRepository.setOnboardingCompleted(true)
                // Also repair locale from restaurant
                restaurantRepository.getRestaurant()?.let {
                    if (it.localeTag != prefs.appLocaleTag) {
                        preferencesRepository.setAppLocaleTag(it.localeTag)
                    }
                }
                AppStartState.Ready
            }
            !isDbComplete && isPrefComplete -> {
                preferencesRepository.setOnboardingCompleted(false)
                AppStartState.RequiresOnboarding
            }
            isDbComplete -> AppStartState.Ready
            else -> AppStartState.RequiresOnboarding
        }
    }.distinctUntilChanged()
}
