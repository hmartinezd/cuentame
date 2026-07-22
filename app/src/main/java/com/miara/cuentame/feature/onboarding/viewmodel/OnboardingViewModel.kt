package com.miara.cuentame.feature.onboarding.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.SetupAreaInput
import com.miara.cuentame.core.domain.repository.SetupCategoryInput
import com.miara.cuentame.core.domain.usecase.CompleteOnboardingUseCase
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingItemDraft
import com.miara.cuentame.feature.onboarding.model.OnboardingItemUiModel
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import com.miara.cuentame.feature.onboarding.model.OnboardingTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val localeTag: String = "en-US",
    val areas: List<OnboardingItemUiModel> = emptyList(),
    val categories: List<OnboardingItemUiModel> = emptyList(),
    val validationErrors: Map<String, Int> = emptyMap(),
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

    private val saveMutex = Mutex()
    private var pendingSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val draft = preferencesRepository.observeOnboardingDraft().first()
            if (draft == null) {
                initializeWithDefaults()
            } else {
                restoreFromDraft(draft)
            }
        }
    }

    private fun initializeWithDefaults() {
        val areas = OnboardingTemplates.SUGGESTED_AREAS.mapIndexed { index, t ->
            OnboardingItemUiModel(
                id = t.key,
                templateKey = t.key,
                labelResId = t.labelResId,
                isSelected = t.defaultSelected,
                sortOrder = index
            )
        }
        val categories = OnboardingTemplates.SUGGESTED_CATEGORIES.mapIndexed { index, t ->
            OnboardingItemUiModel(
                id = t.key,
                templateKey = t.key,
                labelResId = t.labelResId,
                isSelected = false,
                sortOrder = index
            )
        }
        _uiState.update { 
            it.copy(
                isLoading = false,
                areas = areas,
                categories = categories
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
                areas = draft.areas.map { item ->
                    OnboardingItemUiModel(
                        id = item.id,
                        templateKey = item.templateKey,
                        labelResId = item.templateKey?.let { key -> 
                            OnboardingTemplates.SUGGESTED_AREAS.find { it.key == key }?.labelResId 
                        },
                        customName = item.customName,
                        isSelected = item.isSelected,
                        sortOrder = item.sortOrder
                    )
                },
                categories = draft.categories.map { item ->
                    OnboardingItemUiModel(
                        id = item.id,
                        templateKey = item.templateKey,
                        labelResId = item.templateKey?.let { key -> 
                            OnboardingTemplates.SUGGESTED_CATEGORIES.find { it.key == key }?.labelResId 
                        },
                        customName = item.customName,
                        isSelected = item.isSelected,
                        sortOrder = item.sortOrder
                    )
                },
                canGoBack = draft.currentStep != OnboardingStep.WELCOME
            )
        }
        updateCanContinue()
    }

    fun onRestaurantNameChanged(value: String) {
        _uiState.update { it.copy(restaurantName = value, validationErrors = it.validationErrors - "restaurantName") }
        updateCanContinue()
        scheduleSave(debounced = true)
    }

    fun onCurrencySelected(code: String) {
        _uiState.update { it.copy(currencyCode = code) }
        scheduleSave()
    }

    fun onLocaleSelected(tag: String) {
        _uiState.update { it.copy(localeTag = tag) }
        scheduleSave()
    }

    fun onToggleItem(isArea: Boolean, id: String) {
        _uiState.update { state ->
            val list = if (isArea) state.areas else state.categories
            val updated = list.map { 
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            if (isArea) state.copy(areas = updated) else state.copy(categories = updated)
        }
        updateCanContinue()
        scheduleSave()
    }

    fun onAddItem(isArea: Boolean, name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            val list = if (isArea) state.areas else state.categories
            val newItem = OnboardingItemUiModel(
                id = idGenerator.newId(),
                customName = name,
                isSelected = true,
                sortOrder = list.size
            )
            if (isArea) state.copy(areas = list + newItem) else state.copy(categories = list + newItem)
        }
        updateCanContinue()
        scheduleSave()
    }

    fun onRenameCustomItem(isArea: Boolean, id: String, name: String) {
        _uiState.update { state ->
            val list = if (isArea) state.areas else state.categories
            val updated = list.map { 
                if (it.id == id) it.copy(customName = name) else it
            }
            if (isArea) state.copy(areas = updated) else state.copy(categories = updated)
        }
        updateCanContinue()
        scheduleSave(debounced = true)
    }

    fun onRemoveItem(isArea: Boolean, id: String) {
        _uiState.update { state ->
            val list = if (isArea) state.areas else state.categories
            val updated = list.filter { it.id != id }
            if (isArea) state.copy(areas = updated) else state.copy(categories = updated)
        }
        updateCanContinue()
        scheduleSave()
    }

    fun onMoveItem(isArea: Boolean, id: String, up: Boolean) {
        _uiState.update { state ->
            val list = (if (isArea) state.areas else state.categories).toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val targetIndex = if (up) index - 1 else index + 1
            if (index != -1 && targetIndex in list.indices) {
                val item = list.removeAt(index)
                list.add(targetIndex, item)
                val updated = list.mapIndexed { i, it -> it.copy(sortOrder = i) }
                if (isArea) state.copy(areas = updated) else state.copy(categories = updated)
            } else {
                state
            }
        }
        scheduleSave()
    }

    fun onNext(suggestedAreaLabels: Map<String, String>, suggestedCategoryLabels: Map<String, String>) {
        viewModelScope.launch {
            if (validateCurrentStep(suggestedAreaLabels, suggestedCategoryLabels)) {
                flushDraft()
                val current = _uiState.value.currentStep
                val nextStep = when (current) {
                    OnboardingStep.WELCOME -> OnboardingStep.RESTAURANT
                    OnboardingStep.RESTAURANT -> OnboardingStep.AREAS
                    OnboardingStep.AREAS -> OnboardingStep.CATEGORIES
                    OnboardingStep.CATEGORIES -> OnboardingStep.REVIEW
                    OnboardingStep.REVIEW -> return@launch
                }
                _uiState.update { it.copy(currentStep = nextStep, canGoBack = true, validationErrors = emptyMap()) }
                updateCanContinue()
                scheduleSave()
            }
        }
    }

    private fun validateCurrentStep(
        suggestedAreaLabels: Map<String, String>,
        suggestedCategoryLabels: Map<String, String>
    ): Boolean {
        val state = _uiState.value
        val errors = mutableMapOf<String, Int>()
        
        when (state.currentStep) {
            OnboardingStep.RESTAURANT -> {
                if (state.restaurantName.trim().isEmpty()) {
                    errors["restaurantName"] = R.string.error_name_empty
                }
            }
            OnboardingStep.AREAS -> {
                val selected = state.areas.filter { it.isSelected }
                if (selected.isEmpty()) {
                    errors["areas"] = R.string.onboarding_areas_selection_empty
                }
                val names = selected.map { (it.customName ?: suggestedAreaLabels[it.templateKey] ?: it.id).normalizeName() }
                if (names.any { it.isBlank() }) errors["areas"] = R.string.error_name_empty
                if (names.size != names.distinct().size) errors["areas"] = R.string.error_duplicate_name
            }
            OnboardingStep.CATEGORIES -> {
                val selected = state.categories.filter { it.isSelected }
                val names = selected.map { (it.customName ?: suggestedCategoryLabels[it.templateKey] ?: it.id).normalizeName() }
                if (names.any { it.isBlank() }) errors["categories"] = R.string.error_name_empty
                if (names.size != names.distinct().size) errors["categories"] = R.string.error_duplicate_name
            }
            else -> {}
        }
        
        _uiState.update { it.copy(validationErrors = errors) }
        return errors.isEmpty()
    }

    fun onBack() {
        viewModelScope.launch {
            flushDraft()
            val current = _uiState.value.currentStep
            val prevStep = when (current) {
                OnboardingStep.WELCOME -> return@launch
                OnboardingStep.RESTAURANT -> OnboardingStep.WELCOME
                OnboardingStep.AREAS -> OnboardingStep.RESTAURANT
                OnboardingStep.CATEGORIES -> OnboardingStep.AREAS
                OnboardingStep.REVIEW -> OnboardingStep.CATEGORIES
            }
            _uiState.update { it.copy(currentStep = prevStep, canGoBack = prevStep != OnboardingStep.WELCOME, validationErrors = emptyMap()) }
            updateCanContinue()
            scheduleSave()
        }
    }

    fun onEditStep(step: OnboardingStep) {
        viewModelScope.launch {
            flushDraft()
            _uiState.update { it.copy(currentStep = step, canGoBack = true, validationErrors = emptyMap()) }
            updateCanContinue()
            scheduleSave()
        }
    }

    fun onCompleteClicked(suggestedAreaLabels: Map<String, String>, suggestedCategoryLabels: Map<String, String>) {
        val state = _uiState.value
        if (state.isSubmitting) return
        
        _uiState.update { it.copy(isSubmitting = true) }
        
        viewModelScope.launch {
            flushDraft()
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
        return _uiState.value.areas.filter { it.isSelected }.mapIndexed { index, item ->
            SetupAreaInput(item.customName ?: suggestedLabels[item.templateKey] ?: item.id, index)
        }
    }

    private fun getFinalCategories(suggestedLabels: Map<String, String>): List<SetupCategoryInput> {
        return _uiState.value.categories.filter { it.isSelected }.mapIndexed { index, item ->
            SetupCategoryInput(item.customName ?: suggestedLabels[item.templateKey] ?: item.id, index)
        }
    }

    private fun updateCanContinue() {
        val state = _uiState.value
        val canContinue = when (state.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.RESTAURANT -> state.restaurantName.isNotBlank()
            OnboardingStep.AREAS -> state.areas.any { it.isSelected }
            OnboardingStep.CATEGORIES -> true
            OnboardingStep.REVIEW -> !state.isSubmitting
        }
        _uiState.update { it.copy(canContinue = canContinue) }
    }

    private fun scheduleSave(debounced: Boolean = false) {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            if (debounced) delay(300)
            saveDraft()
        }
    }

    private suspend fun flushDraft() {
        pendingSaveJob?.cancel()
        saveDraft()
    }

    private suspend fun saveDraft() {
        val state = _uiState.value
        if (state.isLoading) return
        
        val draft = OnboardingDraft(
            currentStep = state.currentStep,
            restaurantName = state.restaurantName,
            currencyCode = state.currencyCode,
            localeTag = state.localeTag,
            areas = state.areas.map { OnboardingItemDraft(it.id, it.templateKey, it.customName, it.isSelected, it.sortOrder) },
            categories = state.categories.map { OnboardingItemDraft(it.id, it.templateKey, it.customName, it.isSelected, it.sortOrder) }
        )
        
        saveMutex.withLock {
            try {
                preferencesRepository.saveOnboardingDraft(draft)
            } catch (e: Exception) {
                // Ignore autosave failure
            }
        }
    }
}
