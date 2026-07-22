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
        when {
            isDbComplete -> {
                // DB is complete, Room is the source of truth for locale
                val restaurant = restaurantRepository.getRestaurant()
                if (restaurant != null) {
                    if (restaurant.localeTag != prefs.appLocaleTag) {
                        preferencesRepository.setAppLocaleTag(restaurant.localeTag)
                    }
                }
                
                if (!prefs.onboardingCompleted) {
                    preferencesRepository.setOnboardingCompleted(true)
                }
                AppStartState.Ready
            }
            prefs.onboardingCompleted -> {
                // DB incomplete but DataStore says complete -> Repair DataStore
                preferencesRepository.setOnboardingCompleted(false)
                AppStartState.RequiresOnboarding
            }
            else -> AppStartState.RequiresOnboarding
        }
    }.distinctUntilChanged()
}
