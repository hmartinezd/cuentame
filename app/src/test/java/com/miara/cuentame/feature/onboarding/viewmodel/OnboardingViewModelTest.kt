package com.miara.cuentame.feature.onboarding.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
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
        override fun observeIsSetupComplete(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = LocalSetupResult.Success
    }

    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id"
    }

    private val validator = LocalSetupValidator()
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val completeOnboardingUseCase = CompleteOnboardingUseCase(fakeSetupRepository, fakePreferencesRepository)
        viewModel = OnboardingViewModel(fakePreferencesRepository, completeOnboardingUseCase, idGenerator, validator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads defaults when no draft exists`() = runTest {
        runCurrent()
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.currentStep).isEqualTo(OnboardingStep.WELCOME)
            assertThat(state.suggestedAreas).isNotEmpty()
        }
    }

    @Test
    fun `autosave persists changes with debounce`() = runTest {
        runCurrent()
        viewModel.onRestaurantNameChanged("New Name")
        
        // Wait for debounce
        advanceTimeBy(500)
        runCurrent()
        
        assertThat(draftFlow.value?.restaurantName).isEqualTo("New Name")
    }

    @Test
    fun `step navigation works`() = runTest {
        runCurrent()
        viewModel.onNext()
        runCurrent()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.RESTAURANT)
        
        viewModel.onBack()
        runCurrent()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
    }
}
