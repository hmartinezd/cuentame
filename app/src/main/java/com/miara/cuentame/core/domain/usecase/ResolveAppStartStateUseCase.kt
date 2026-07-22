package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        // We use a flow here if we want it to react, but setupRepository.isSetupComplete is suspend.
        // Usually, we wrap suspend in a flow if it can change or just fetch it.
        // For startup, we can just fetch once or wrap in a periodic check if needed.
        // Let's assume we fetch it once or wrap in a flow.
        // Actually, let's just make it a Flow by emitting once or combining with preferences.
        preferencesRepository.observePreferences() // Just to trigger
    ) { prefs, _ ->
        val isDbComplete = setupRepository.isSetupComplete()
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
    }
}
