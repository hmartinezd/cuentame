package com.miara.cuentame.core.domain.usecase

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

class CompleteOnboardingUseCaseTest {

    private val preferencesFlow = MutableStateFlow(AppPreferences.DEFAULT)
    private var setupResult: LocalSetupResult = LocalSetupResult.Success
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
        override suspend fun isSetupComplete(): Boolean = true
        override fun observeIsSetupComplete(): Flow<Boolean> = MutableStateFlow(true)
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = setupResult
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<com.miara.cuentame.core.model.restaurant.Restaurant?> = MutableStateFlow(restaurantValue)
        override suspend fun getRestaurant(): com.miara.cuentame.core.model.restaurant.Restaurant? = restaurantValue
        override suspend fun save(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) {
            restaurantValue = restaurant
        }
    }

    private val useCase = CompleteOnboardingUseCase(fakeSetupRepository, fakeRestaurantRepository, fakePreferencesRepository)

    @Test
    fun `Success updates DataStore and clears draft`() = runTest {
        val command = CompleteLocalSetupCommand("Rest", "USD", "es-US", emptyList(), emptyList())
        setupResult = LocalSetupResult.Success
        
        val result = useCase(command)
        
        assertThat(result).isEqualTo(LocalSetupResult.Success)
        assertThat(preferencesFlow.value.onboardingCompleted).isTrue()
        assertThat(preferencesFlow.value.appLocaleTag).isEqualTo("es-US")
    }

    @Test
    fun `AlreadyCompleted uses Room locale as authoritative`() = runTest {
        // Existing restaurant is Spanish
        restaurantValue = com.miara.cuentame.core.model.restaurant.Restaurant(
            id = com.miara.cuentame.core.common.ids.RestaurantId("id"),
            name = "Test",
            currencyCode = "USD",
            localeTag = "es-US",
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
        
        // Stale command says English
        val command = CompleteLocalSetupCommand("Rest", "USD", "en-US", emptyList(), emptyList())
        setupResult = LocalSetupResult.AlreadyCompleted
        
        val result = useCase(command)
        
        assertThat(result).isEqualTo(LocalSetupResult.AlreadyCompleted)
        assertThat(preferencesFlow.value.onboardingCompleted).isTrue()
        assertThat(preferencesFlow.value.appLocaleTag).isEqualTo("es-US")
    }
}
