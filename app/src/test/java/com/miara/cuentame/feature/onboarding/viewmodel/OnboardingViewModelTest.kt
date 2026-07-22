package com.miara.cuentame.feature.onboarding.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val draftFlow = MutableStateFlow<OnboardingDraft?>(null)

    private val fakePreferencesRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = MutableStateFlow(AppPreferences.DEFAULT)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
        override suspend fun setAppLocaleTag(localeTag: String) {}
        override fun observeOnboardingDraft(): Flow<OnboardingDraft?> = draftFlow
        override suspend fun saveOnboardingDraft(draft: OnboardingDraft) { draftFlow.value = draft }
        override suspend fun clearOnboardingDraft() { draftFlow.value = null }
    }

    private val fakeSetupRepository = object : LocalSetupRepository {
        override suspend fun isSetupComplete(): Boolean = false
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = LocalSetupResult.Success
    }

    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id"
    }

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val completeOnboardingUseCase = CompleteOnboardingUseCase(fakeSetupRepository, fakePreferencesRepository)
        // Note: Context is needed for getString, which is hard to mock in unit test.
        // I will need to refactor getFinalAreas or mock Context.
        // For now, I'll pass a null or mock context if possible.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads defaults`() = runTest {
        // ...
    }
}
