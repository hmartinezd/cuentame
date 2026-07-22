package com.miara.cuentame.feature.onboarding.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.SetupAreaInput
import com.miara.cuentame.core.domain.repository.SetupCategoryInput
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.EditableNameUiModel
import com.miara.cuentame.feature.onboarding.model.OnboardingCustomItemDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import com.miara.cuentame.feature.onboarding.model.OnboardingTemplates
import com.miara.cuentame.feature.onboarding.model.SelectableTemplateUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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
    val canGoBack: Boolean = false,
    val canContinue: Boolean = false
)

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
    data class ShowError(val error: Throwable) : OnboardingEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val idGenerator: IdGenerator,
    private val validator: LocalSetupValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val draft = preferencesRepository.observeOnboardingDraft().first()
            if (draft == null) {
                initializeWithDefaults()
            } else {
                restoreFromDraft(draft)
            }
            
            observeStateForAutosave()
        }
    }

    private fun initializeWithDefaults() {
        _uiState.update { 
            it.copy(
                isLoading = false,
                suggestedAreas = OnboardingTemplates.SUGGESTED_AREAS.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, t.defaultSelected)
                },
                suggestedCategories = OnboardingTemplates.SUGGESTED_CATEGORIES.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, false)
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
                customAreas = draft.customAreas.map { EditableNameUiModel(it.id, it.name) },
                suggestedCategories = OnboardingTemplates.SUGGESTED_CATEGORIES.map { t ->
                    SelectableTemplateUiModel(t.key, t.labelResId, draft.selectedSuggestedCategoryKeys.contains(t.key))
                },
                customCategories = draft.customCategories.map { EditableNameUiModel(it.id, it.name) },
                canGoBack = draft.currentStep != OnboardingStep.WELCOME
            )
        }
        updateCanContinue()
    }

    private fun observeStateForAutosave() {
        viewModelScope.launch {
            _uiState
                .drop(1)
                .map { state ->
                    OnboardingDraft(
                        currentStep = state.currentStep,
                        restaurantName = state.restaurantName,
                        currencyCode = state.currencyCode,
                        localeTag = state.localeTag,
                        selectedSuggestedAreaKeys = state.suggestedAreas.filter { it.isSelected }.map { it.key }.toSet(),
                        customAreas = state.customAreas.mapIndexed { i, it -> OnboardingCustomItemDraft(it.id, it.name, i) },
                        selectedSuggestedCategoryKeys = state.suggestedCategories.filter { it.isSelected }.map { it.key }.toSet(),
                        customCategories = state.customCategories.mapIndexed { i, it -> OnboardingCustomItemDraft(it.id, it.name, i) }
                    )
                }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { draft ->
                    preferencesRepository.saveOnboardingDraft(draft)
                }
        }
    }

    fun onRestaurantNameChanged(value: String) {
        _uiState.update { it.copy(restaurantName = value) }
        updateCanContinue()
    }

    fun onCurrencySelected(code: String) {
        _uiState.update { it.copy(currencyCode = code) }
    }

    fun onLocaleSelected(tag: String) {
        _uiState.update { it.copy(localeTag = tag) }
    }

    fun onSuggestedAreaToggled(key: String) {
        _uiState.update { state ->
            val updated = state.suggestedAreas.map { 
                if (it.key == key) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(suggestedAreas = updated)
        }
        updateCanContinue()
    }

    fun onCustomAreaAdded(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            state.copy(customAreas = state.customAreas + EditableNameUiModel(idGenerator.newId(), name))
        }
        updateCanContinue()
    }

    fun onCustomAreaRenamed(id: String, name: String) {
        _uiState.update { state ->
            val updated = state.customAreas.map { 
                if (it.id == id) it.copy(name = name) else it
            }
            state.copy(customAreas = updated)
        }
        updateCanContinue()
    }

    fun onCustomAreaRemoved(id: String) {
        _uiState.update { state ->
            state.copy(customAreas = state.customAreas.filter { it.id != id })
        }
        updateCanContinue()
    }

    fun onAreaMovedUp(id: String) {
        _uiState.update { state ->
            val list = state.customAreas.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index > 0) {
                val item = list.removeAt(index)
                list.add(index - 1, item)
            }
            state.copy(customAreas = list)
        }
    }

    fun onAreaMovedDown(id: String) {
        _uiState.update { state ->
            val list = state.customAreas.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index != -1 && index < list.size - 1) {
                val item = list.removeAt(index)
                list.add(index + 1, item)
            }
            state.copy(customAreas = list)
        }
    }

    fun onSuggestedCategoryToggled(key: String) {
        _uiState.update { state ->
            val updated = state.suggestedCategories.map { 
                if (it.key == key) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(suggestedCategories = updated)
        }
    }

    fun onCustomCategoryAdded(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            state.copy(customCategories = state.customCategories + EditableNameUiModel(idGenerator.newId(), name))
        }
    }

    fun onCustomCategoryRenamed(id: String, name: String) {
        _uiState.update { state ->
            val updated = state.customCategories.map { 
                if (it.id == id) it.copy(name = name) else it
            }
            state.copy(customCategories = updated)
        }
    }

    fun onCustomCategoryRemoved(id: String) {
        _uiState.update { state ->
            state.copy(customCategories = state.customCategories.filter { it.id != id })
        }
    }

    fun onCategoryMovedUp(id: String) {
        _uiState.update { state ->
            val list = state.customCategories.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index > 0) {
                val item = list.removeAt(index)
                list.add(index - 1, item)
            }
            state.copy(customCategories = list)
        }
    }

    fun onCategoryMovedDown(id: String) {
        _uiState.update { state ->
            val list = state.customCategories.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index != -1 && index < list.size - 1) {
                val item = list.removeAt(index)
                list.add(index + 1, item)
            }
            state.copy(customCategories = list)
        }
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
        updateCanContinue()
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
        updateCanContinue()
    }

    fun onEditStep(step: OnboardingStep) {
        _uiState.update { it.copy(currentStep = step, canGoBack = true) }
        updateCanContinue()
    }

    fun onCompleteClicked(suggestedAreaLabels: Map<String, String>, suggestedCategoryLabels: Map<String, String>) {
        val state = _uiState.value
        if (state.isSubmitting) return
        
        _uiState.update { it.copy(isSubmitting = true) }
        
        viewModelScope.launch {
            val command = CompleteLocalSetupCommand(
                restaurantName = state.restaurantName,
                currencyCode = state.currencyCode,
                localeTag = state.localeTag,
                areas = getFinalAreas(suggestedAreaLabels),
                categories = getFinalCategories(suggestedCategoryLabels)
            )
            
            try {
                validator.validate(command)
                when (val result = completeOnboardingUseCase(command)) {
                    LocalSetupResult.Success, LocalSetupResult.AlreadyCompleted -> {
                        _events.send(OnboardingEvent.NavigateToHome)
                    }
                    is LocalSetupResult.Failure -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        _events.send(OnboardingEvent.ShowError(result.error))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
                _events.send(OnboardingEvent.ShowError(e))
            }
        }
    }

    private fun getFinalAreas(suggestedLabels: Map<String, String>): List<SetupAreaInput> {
        val state = _uiState.value
        val suggested = state.suggestedAreas.filter { it.isSelected }.map { 
            suggestedLabels[it.key] ?: it.key
        }
        val custom = state.customAreas.map { it.name }
        return (suggested + custom).mapIndexed { index, name ->
            SetupAreaInput(name, index)
        }
    }

    private fun getFinalCategories(suggestedLabels: Map<String, String>): List<SetupCategoryInput> {
        val state = _uiState.value
        val suggested = state.suggestedCategories.filter { it.isSelected }.map { 
            suggestedLabels[it.key] ?: it.key
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
}
