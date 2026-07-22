package com.miara.cuentame.feature.onboarding.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.SetupAreaInput
import com.miara.cuentame.core.domain.repository.SetupCategoryInput
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.EditableNameUiModel
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import com.miara.cuentame.feature.onboarding.model.OnboardingTemplates
import com.miara.cuentame.feature.onboarding.model.SelectableTemplateUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val localeTag: String = "en-US",
    val suggestedAreas: List<SelectableTemplateUiModel> = emptyList(),
    val customAreas: List<EditableNameUiModel> = emptyList(),
    val suggestedCategories: List<SelectableTemplateUiModel> = emptyList(),
    val customCategories: List<EditableNameUiModel> = emptyList(),
    val validationErrors: Map<String, String> = emptyMap(),
    val canGoBack: Boolean = false,
    val canContinue: Boolean = false
)

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
    data class ShowError(val message: String) : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val idGenerator: IdGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    init {
        preferencesRepository.observeOnboardingDraft()
            .onEach { draft ->
                if (draft == null) {
                    initializeWithDefaults()
                } else {
                    restoreFromDraft(draft)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun initializeWithDefaults() {
        _uiState.update { 
            it.copy(
                isLoading = false,
                suggestedAreas = OnboardingTemplates.SUGGESTED_AREAS.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, t.defaultSelected)
                },
                suggestedCategories = OnboardingTemplates.SUGGESTED_CATEGORIES.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, t.defaultSelected)
                }
            )
        }
        updateCanContinue()
    }

    private fun restoreFromDraft(draft: OnboardingDraft) {
        _uiState.update { 
            it.copy(
                isLoading = false,
                currentStep = draft.currentStep,
                restaurantName = draft.restaurantName,
                currencyCode = draft.currencyCode,
                localeTag = draft.localeTag,
                suggestedAreas = OnboardingTemplates.SUGGESTED_AREAS.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, draft.selectedSuggestedAreaKeys.contains(t.key))
                },
                customAreas = draft.customAreaNames.map { name -> EditableNameUiModel(idGenerator.newId(), name) },
                suggestedCategories = OnboardingTemplates.SUGGESTED_CATEGORIES.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, draft.selectedSuggestedCategoryKeys.contains(t.key))
                },
                customCategories = draft.customCategoryNames.map { name -> EditableNameUiModel(idGenerator.newId(), name) }
            )
        }
        updateCanContinue()
    }

    fun onRestaurantNameChanged(value: String) {
        _uiState.update { it.copy(restaurantName = value) }
        updateCanContinue()
        saveDraft()
    }

    fun onCurrencySelected(code: String) {
        _uiState.update { it.copy(currencyCode = code) }
        saveDraft()
    }

    fun onLocaleSelected(tag: String) {
        _uiState.update { it.copy(localeTag = tag) }
        saveDraft()
    }

    fun onSuggestedAreaToggled(key: String) {
        _uiState.update { state ->
            val updated = state.suggestedAreas.map { 
                if (it.key == key) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(suggestedAreas = updated)
        }
        updateCanContinue()
        saveDraft()
    }

    fun onSuggestedCategoryToggled(key: String) {
        _uiState.update { state ->
            val updated = state.suggestedCategories.map { 
                if (it.key == key) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(suggestedCategories = updated)
        }
        saveDraft()
    }

    fun onCustomCategoryAdded(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            state.copy(customCategories = state.customCategories + EditableNameUiModel(idGenerator.newId(), name))
        }
        saveDraft()
    }

    fun onCustomCategoryRemoved(id: String) {
        _uiState.update { state ->
            state.copy(customCategories = state.customCategories.filter { it.id != id })
        }
        saveDraft()
    }

    fun onCustomAreaAdded(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            state.copy(customAreas = state.customAreas + EditableNameUiModel(idGenerator.newId(), name))
        }
        updateCanContinue()
        saveDraft()
    }

    fun onCustomAreaRemoved(id: String) {
        _uiState.update { state ->
            state.copy(customAreas = state.customAreas.filter { it.id != id })
        }
        updateCanContinue()
        saveDraft()
    }

    fun onNext() {
        val current = _uiState.value.currentStep
        val nextStep = when (current) {
            OnboardingStep.WELCOME -> OnboardingStep.RESTAURANT
            OnboardingStep.RESTAURANT -> OnboardingStep.AREAS
            OnboardingStep.AREAS -> OnboardingStep.CATEGORIES
            OnboardingStep.CATEGORIES -> OnboardingStep.REVIEW
            OnboardingStep.REVIEW -> return
        }
        _uiState.update { it.copy(currentStep = nextStep, canGoBack = true) }
        saveDraft()
    }

    fun onBack() {
        val current = _uiState.value.currentStep
        val prevStep = when (current) {
            OnboardingStep.WELCOME -> return
            OnboardingStep.RESTAURANT -> OnboardingStep.WELCOME
            OnboardingStep.AREAS -> OnboardingStep.RESTAURANT
            OnboardingStep.CATEGORIES -> OnboardingStep.AREAS
            OnboardingStep.REVIEW -> OnboardingStep.CATEGORIES
        }
        _uiState.update { it.copy(currentStep = prevStep, canGoBack = prevStep != OnboardingStep.WELCOME) }
        saveDraft()
    }

    fun onCompleteClicked() {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true) }
        
        viewModelScope.launch {
            val command = CompleteLocalSetupCommand(
                restaurantName = state.restaurantName,
                currencyCode = state.currencyCode,
                localeTag = state.localeTag,
                areas = getFinalAreas(),
                categories = getFinalCategories()
            )
            
            when (val result = completeOnboardingUseCase(command)) {
                LocalSetupResult.Success, LocalSetupResult.AlreadyCompleted -> {
                    _events.emit(OnboardingEvent.NavigateToHome)
                }
                is LocalSetupResult.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    val message = when(result.error) {
                        is ValidationError -> context.getString(
                            when(result.error) {
                                ValidationError.InvalidName -> R.string.error_name_empty
                                ValidationError.DuplicateActiveName -> R.string.error_duplicate_name
                                ValidationError.NoActiveInventoryArea -> R.string.onboarding_areas_selection_empty
                                else -> R.string.error_generic
                            }
                        )
                        else -> context.getString(R.string.error_generic)
                    }
                    _events.emit(OnboardingEvent.ShowError(message))
                }
            }
        }
    }

    private fun getFinalAreas(): List<SetupAreaInput> {
        val state = _uiState.value
        val suggested = state.suggestedAreas.filter { it.isSelected }.map { 
            context.getString(it.labelResId) 
        }
        val custom = state.customAreas.map { it.name }
        return (suggested + custom).mapIndexed { index, name ->
            SetupAreaInput(name, index)
        }
    }

    private fun getFinalCategories(): List<SetupCategoryInput> {
        val state = _uiState.value
        val suggested = state.suggestedCategories.filter { it.isSelected }.map { 
            context.getString(it.labelResId) 
        }
        val custom = state.customCategories.map { it.name }
        return (suggested + custom).mapIndexed { index, name ->
            SetupCategoryInput(name, index)
        }
    }

    private fun updateCanContinue() {
        val state = _uiState.value
        val canContinue = when (state.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.RESTAURANT -> state.restaurantName.isNotBlank()
            OnboardingStep.AREAS -> state.suggestedAreas.any { it.isSelected } || state.customAreas.isNotEmpty()
            OnboardingStep.CATEGORIES -> true
            OnboardingStep.REVIEW -> !state.isSubmitting
        }
        _uiState.update { it.copy(canContinue = canContinue) }
    }

    private fun saveDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesRepository.saveOnboardingDraft(
                OnboardingDraft(
                    currentStep = state.currentStep,
                    restaurantName = state.restaurantName,
                    currencyCode = state.currencyCode,
                    localeTag = state.localeTag,
                    selectedSuggestedAreaKeys = state.suggestedAreas.filter { it.isSelected }.map { it.key }.toSet(),
                    customAreaNames = state.customAreas.map { it.name },
                    selectedSuggestedCategoryKeys = state.suggestedCategories.filter { it.isSelected }.map { it.key }.toSet(),
                    customCategoryNames = state.customCategories.map { it.name }
                )
            )
        }
    }
}
