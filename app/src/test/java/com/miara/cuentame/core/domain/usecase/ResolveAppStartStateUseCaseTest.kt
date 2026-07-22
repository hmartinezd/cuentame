package com.miara.cuentame.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveAppStartStateUseCaseTest {

    private val preferencesFlow = MutableStateFlow(AppPreferences.DEFAULT)
    private val dbCompleteFlow = MutableStateFlow(false)

    private val fakePreferencesRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferencesFlow
        override suspend fun setOnboardingCompleted(completed: Boolean) {
            preferencesFlow.value = preferencesFlow.value.copy(onboardingCompleted = completed)
        }
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
        override suspend fun setAppLocaleTag(localeTag: String) {}
        override fun observeOnboardingDraft(): Flow<OnboardingDraft?> = MutableStateFlow(null)
        override suspend fun saveOnboardingDraft(draft: OnboardingDraft) {}
        override suspend fun clearOnboardingDraft() {}
    }

    private val fakeSetupRepository = object : LocalSetupRepository {
        override suspend fun isSetupComplete(): Boolean = dbCompleteFlow.value
        override fun observeIsSetupComplete(): Flow<Boolean> = dbCompleteFlow
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = LocalSetupResult.Success
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<com.miara.cuentame.core.model.restaurant.Restaurant?> = MutableStateFlow(null)
        override suspend fun getRestaurant(): com.miara.cuentame.core.model.restaurant.Restaurant? = null
        override suspend fun save(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) {}
    }

    private val useCase = ResolveAppStartStateUseCase(fakePreferencesRepository, fakeSetupRepository, fakeRestaurantRepository)

    @Test
    fun `both incomplete returns RequiresOnboarding`() = runTest {
        dbCompleteFlow.value = false
        preferencesFlow.value = AppPreferences.DEFAULT.copy(onboardingCompleted = false)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.RequiresOnboarding)
        }
    }

    @Test
    fun `both complete returns Ready`() = runTest {
        dbCompleteFlow.value = true
        preferencesFlow.value = AppPreferences.DEFAULT.copy(onboardingCompleted = true)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.Ready)
        }
    }

    @Test
    fun `db complete but pref incomplete repairs pref and returns Ready`() = runTest {
        dbCompleteFlow.value = true
        preferencesFlow.value = AppPreferences.DEFAULT.copy(onboardingCompleted = false)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.Ready)
            assertThat(preferencesFlow.value.onboardingCompleted).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
