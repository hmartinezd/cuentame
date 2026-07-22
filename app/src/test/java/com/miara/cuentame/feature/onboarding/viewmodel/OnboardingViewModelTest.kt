package com.miara.cuentame.feature.onboarding.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.domain.validation.ValidationError
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
    private var draftValue: OnboardingDraft? = null
    private var shouldFailSave = false

    private val fakePreferencesRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = MutableStateFlow(AppPreferences.DEFAULT)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
        override suspend fun setAppLocaleTag(localeTag: String) {}
        override suspend fun loadOnboardingDraft(): OnboardingDraft? = draftValue
        override suspend fun saveOnboardingDraft(draft: OnboardingDraft) {
            if (shouldFailSave) throw ValidationError.OnboardingDraftSaveFailed
            draftValue = draft 
        }
        override suspend fun clearOnboardingDraft() { draftValue = null }
    }

    private val fakeSetupRepository = object : LocalSetupRepository {
        override suspend fun isSetupComplete(): Boolean = false
        override fun observeIsSetupComplete(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult = LocalSetupResult.Success
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<com.miara.cuentame.core.model.restaurant.Restaurant?> = MutableStateFlow(null)
        override suspend fun getRestaurant(): com.miara.cuentame.core.model.restaurant.Restaurant? = null
        override suspend fun save(restaurant: com.miara.cuentame.core.model.restaurant.Restaurant) {}
    }

    private var idCounter = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id_${++idCounter}"
    }

    private val validator = LocalSetupValidator()
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val completeOnboardingUseCase = CompleteOnboardingUseCase(fakeSetupRepository, fakeRestaurantRepository, fakePreferencesRepository)
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
            assertThat(state.areas).isNotEmpty()
        }
    }

    @Test
    fun `autosave persists changes with debounce for name`() = runTest {
        runCurrent()
        viewModel.onRestaurantNameChanged("New Name")
        runCurrent()
        
        assertThat(draftValue).isNull()
        
        advanceTimeBy(500)
        runCurrent()
        
        assertThat(draftValue?.restaurantName).isEqualTo("New Name")
    }

    @Test
    fun `autosave persists selections immediately`() = runTest {
        runCurrent()
        val areaId = viewModel.uiState.value.areas.first().id
        viewModel.onToggleItem(isArea = true, id = areaId)
        
        runCurrent()
        
        val area = viewModel.uiState.value.areas.find { it.id == areaId }!!
        assertThat(draftValue?.areas?.find { it.id == areaId }?.isSelected).isEqualTo(area.isSelected)
    }

    @Test
    fun `next step persists the NEW step immediately`() = runTest {
        runCurrent()
        viewModel.onNext(emptyMap(), emptyMap()) // WELCOME to RESTAURANT
        runCurrent()
        
        assertThat(draftValue?.currentStep).isEqualTo(OnboardingStep.RESTAURANT)
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.RESTAURANT)
    }

    @Test
    fun `reordering updates sort order and persists immediately`() = runTest {
        runCurrent()
        val secondId = viewModel.uiState.value.areas[1].id
        
        viewModel.onMoveItem(isArea = true, id = secondId, up = true)
        runCurrent()
        
        val updatedAreas = viewModel.uiState.value.areas
        assertThat(updatedAreas.first().id).isEqualTo(secondId)
        assertThat(updatedAreas.first().sortOrder).isEqualTo(0)
        
        assertThat(draftValue?.areas?.first()?.id).isEqualTo(secondId)
        assertThat(draftValue?.areas?.first()?.sortOrder).isEqualTo(0)
    }

    @Test
    fun `draft save failure is visible in UI state`() = runTest {
        runCurrent()
        shouldFailSave = true
        
        viewModel.onCurrencySelected("EUR")
        runCurrent()
        
        assertThat(viewModel.uiState.value.draftSaveError).isNotNull()
        assertThat(viewModel.uiState.value.draftSaveError).isEqualTo(ValidationError.OnboardingDraftSaveFailed)
    }
}
