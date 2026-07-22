package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.LocalSetupRepository
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
    private val setupRepository: LocalSetupRepository
) {
    operator fun invoke(): Flow<AppStartState> = combine(
        preferencesRepository.observePreferences(),
        setupRepository.observeIsSetupComplete()
    ) { prefs, isDbComplete ->
        val isPrefComplete = prefs.onboardingCompleted

        when {
            isDbComplete && !isPrefComplete -> {
                preferencesRepository.setOnboardingCompleted(true)
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
