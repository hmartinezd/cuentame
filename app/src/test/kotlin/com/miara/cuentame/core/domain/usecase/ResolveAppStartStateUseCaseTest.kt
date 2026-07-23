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
    private var restaurantValue: com.miara.cuentame.core.model.restaurant.Restaurant? = null

    private val fakePreferencesRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferencesFlow
        override suspend fun setOnboardingCompleted(completed: Boolean) {
            preferencesFlow.value = preferencesFlow.value.copy(onboardingCompleted = completed)
        }
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
        override suspend fun setAppLocaleTag(localeTag: String) {
            preferencesFlow.value = preferencesFlow.value.copy(appLocaleTag = localeTag)
        }
        override suspend fun loadOnboardingDraft(): OnboardingDraft? = null
        override suspend fun saveOnboardingDraft(draft: OnboardingDraft) {}
        override suspend fun clearOnboardingDraft() {}
    }

    private val fakeSetupRepository = object : LocalSetupRepository {
        override suspend fun isSetupComplete(): Boolean = dbCompleteFlow.value
        override fun observeIsSetupComplete(): Flow<Boolean> = dbCompleteFlow
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = LocalSetupResult.Success
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<com.miara.cuentame.core.model.restaurant.Restaurant?> = MutableStateFlow(restaurantValue)
        override suspend fun getRestaurant(): com.miara.cuentame.core.model.restaurant.Restaurant? = restaurantValue
        override suspend fun save(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) {
            restaurantValue = restaurant
        }
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
        restaurantValue = com.miara.cuentame.core.model.restaurant.Restaurant(
            id = com.miara.cuentame.core.common.ids.RestaurantId("id"),
            name = "Test",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.Ready)
        }
    }

    @Test
    fun `db complete but locale mismatch repairs DataStore and returns Ready`() = runTest {
        dbCompleteFlow.value = true
        preferencesFlow.value = AppPreferences.DEFAULT.copy(onboardingCompleted = true, appLocaleTag = "en-US")
        restaurantValue = com.miara.cuentame.core.model.restaurant.Restaurant(
            id = com.miara.cuentame.core.common.ids.RestaurantId("id"),
            name = "Test",
            currencyCode = "USD",
            localeTag = "es-US",
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.Ready)
            assertThat(preferencesFlow.value.appLocaleTag).isEqualTo("es-US")
        }
    }

    @Test
    fun `db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding`() = runTest {
        dbCompleteFlow.value = false
        preferencesFlow.value = AppPreferences.DEFAULT.copy(onboardingCompleted = true)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(AppStartState.RequiresOnboarding)
            assertThat(preferencesFlow.value.onboardingCompleted).isFalse()
        }
    }
}
