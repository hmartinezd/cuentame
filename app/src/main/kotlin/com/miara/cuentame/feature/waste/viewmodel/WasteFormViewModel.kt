package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UpdateWasteDraftCommand
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.usecase.CreateWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.PreviewWasteUseCase
import com.miara.cuentame.core.domain.usecase.UpdateWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.WastePreview
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.WasteReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class WasteUnitOptionUi(
    val id: IngredientUnitOptionId,
    val label: String,
    val factorToBase: BigDecimal,
    val isSelected: Boolean,
    val isSelectable: Boolean,
    val isActive: Boolean
)

sealed interface WasteFormScreenState {
    data object Loading : WasteFormScreenState
    data object Ready : WasteFormScreenState
    data object NotFound : WasteFormScreenState
    data object InvalidRoute : WasteFormScreenState
    data object OwnershipMismatch : WasteFormScreenState
}

data class WasteFormUiState(
    val screenState: WasteFormScreenState = WasteFormScreenState.Loading,
    val isSaving: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val wasteEventId: WasteEventId? = null,
    val restaurantId: RestaurantId? = null,
    val selectedIngredientId: IngredientId? = null,
    val selectedAreaId: InventoryAreaId? = null,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val quantityText: String = "",
    val selectedReason: WasteReason? = null,
    val effectiveAt: Instant = Instant.now(),
    val notes: String = "",
    val attachmentUri: String? = null,
    val preview: WastePreview? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val areas: List<InventoryArea> = emptyList(),
    val unitOptions: List<WasteUnitOptionUi> = emptyList(),
    val error: Throwable? = null,
    val canSave: Boolean = false
)

sealed interface WasteFormEvent {
    data class Success(val wasteEventId: WasteEventId) : WasteFormEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WasteFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wasteRepository: WasteRepository,
    private val ingredientRepository: IngredientRepository,
    private val areaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    private val createWasteDraftUseCase: CreateWasteDraftUseCase,
    private val updateWasteDraftUseCase: UpdateWasteDraftUseCase,
    private val previewWasteUseCase: PreviewWasteUseCase
) : ViewModel() {

    private val wasteEventIdStr: String? = savedStateHandle["wasteEventId"]
    private val wasteEventId = wasteEventIdStr?.let { WasteEventId(it) }

    private val _isSaving = MutableStateFlow(false)
    private val _isLoadingPreview = MutableStateFlow(false)
    private val _selectedIngredientId = MutableStateFlow<IngredientId?>(null)
    private val _selectedAreaId = MutableStateFlow<InventoryAreaId?>(null)
    private val _selectedUnitOptionId = MutableStateFlow<IngredientUnitOptionId?>(null)
    private val _quantityText = MutableStateFlow("")
    private val _selectedReason = MutableStateFlow<WasteReason?>(null)
    private val _effectiveAt = MutableStateFlow(Instant.now())
    private val _notes = MutableStateFlow("")
    private val _attachmentUri = MutableStateFlow<String?>(null)
    private val _preview = MutableStateFlow<WastePreview?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _events = MutableSharedFlow<WasteFormEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var previewJob: Job? = null

    val uiState: StateFlow<WasteFormUiState> = restaurantRepository.observeRestaurant()
        .filterNotNull()
        .flatMapLatest { restaurant ->
            combine(
                if (wasteEventId != null) wasteRepository.observeWasteEvent(wasteEventId) else flowOf(null),
                ingredientRepository.observeIngredients(restaurant.id, false),
                areaRepository.observeActiveAreas(),
                _selectedIngredientId,
                _selectedAreaId,
                _selectedUnitOptionId,
                _quantityText,
                _selectedReason,
                _effectiveAt,
                _notes,
                _attachmentUri,
                _preview,
                _isSaving,
                _isLoadingPreview,
                _error,
                _hasLoadedOnce
            ) { args ->
                val existingDetails = args[0] as com.miara.cuentame.core.domain.repository.WasteDetails?
                val activeIngredients = args[1] as List<Ingredient>
                val activeAreas = args[2] as List<InventoryArea>
                val selIngId = args[3] as IngredientId?
                val selAreaId = args[4] as InventoryAreaId?
                val selUnitId = args[5] as IngredientUnitOptionId?
                val qtyText = args[6] as String
                val reason = args[7] as WasteReason?
                val effAt = args[8] as Instant
                val notes = args[9] as String
                val attUri = args[10] as String?
                val preview = args[11] as WastePreview?
                val isSaving = args[12] as Boolean
                val isLoadingPreview = args[13] as Boolean
                val error = args[14] as Throwable?
                val hasLoadedOnce = args[15] as Boolean

                val screenState = when {
                    wasteEventId != null && !hasLoadedOnce && error == null -> WasteFormScreenState.Loading
                    wasteEventId != null && existingDetails == null && error != null -> WasteFormScreenState.Ready
                    wasteEventId != null && existingDetails == null -> WasteFormScreenState.NotFound
                    wasteEventId != null && existingDetails?.event?.restaurantId != restaurant.id -> WasteFormScreenState.OwnershipMismatch
                    else -> WasteFormScreenState.Ready
                }

                val canSave = selIngId != null && selAreaId != null && selUnitId != null && 
                              reason != null && DecimalParser.parse(qtyText)?.let { it > BigDecimal.ZERO } == true &&
                              !isSaving

                // Rebuild unit options if ingredient is selected
                val unitOptions = if (selIngId != null) {
                    val options = ingredientRepository.getUnitOptions(selIngId, true)
                    options.map { opt ->
                        val isSelected = opt.id == selUnitId
                        WasteUnitOptionUi(
                            id = opt.id,
                            label = opt.shortLabel,
                            factorToBase = opt.factorToBase,
                            isSelected = isSelected,
                            isSelectable = (opt.isActive || isSelected) && opt.factorToBase > BigDecimal.ZERO,
                            isActive = opt.isActive
                        )
                    }
                } else emptyList()

                WasteFormUiState(
                    screenState = screenState,
                    isSaving = isSaving,
                    isLoadingPreview = isLoadingPreview,
                    wasteEventId = wasteEventId,
                    restaurantId = restaurant.id,
                    selectedIngredientId = selIngId,
                    selectedAreaId = selAreaId,
                    selectedUnitOptionId = selUnitId,
                    quantityText = qtyText,
                    selectedReason = reason,
                    effectiveAt = effAt,
                    notes = notes,
                    attachmentUri = attUri,
                    preview = preview,
                    ingredients = activeIngredients,
                    areas = activeAreas,
                    unitOptions = unitOptions,
                    error = error,
                    canSave = canSave
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WasteFormUiState()
        )

    init {
        if (wasteEventId != null) {
            viewModelScope.launch {
                val existing = wasteRepository.getById(wasteEventId)
                if (existing != null) {
                    if (existing.status != DocumentStatus.DRAFT) {
                        _error.value = ValidationError.WasteEventImmutable
                    } else {
                        _selectedIngredientId.value = existing.ingredientId
                        _selectedAreaId.value = existing.areaId
                        _selectedUnitOptionId.value = existing.ingredientUnitOptionId
                        _quantityText.value = existing.quantityEntered.toPlainString()
                        _selectedReason.value = existing.reason
                        _effectiveAt.value = existing.effectiveAt
                        _notes.value = existing.notes ?: ""
                        _attachmentUri.value = existing.attachmentPath
                    }
                }
                _hasLoadedOnce.value = true
            }
        } else {
            _hasLoadedOnce.value = true
        }
    }

    fun onIngredientSelected(id: IngredientId) {
        if (_selectedIngredientId.value == id) return
        _selectedIngredientId.value = id
        
        viewModelScope.launch {
            val options = ingredientRepository.getUnitOptions(id, true)
            val defaultOption = options.find { it.isDefaultCount } ?: options.find { it.isBase }
            _selectedUnitOptionId.value = defaultOption?.id
            updatePreview()
        }
    }

    fun onAreaSelected(id: InventoryAreaId) {
        if (_selectedAreaId.value == id) return
        _selectedAreaId.value = id
        updatePreview()
    }

    fun onUnitOptionSelected(id: IngredientUnitOptionId) {
        if (_selectedUnitOptionId.value == id) return
        _selectedUnitOptionId.value = id
        updatePreview()
    }

    fun onQuantityChanged(text: String) {
        if (_quantityText.value == text) return
        _quantityText.value = text
        updatePreview()
    }

    fun onReasonSelected(reason: WasteReason) {
        _selectedReason.value = reason
    }

    fun onEffectiveAtChanged(instant: Instant) {
        if (_effectiveAt.value == instant) return
        _effectiveAt.value = instant
        updatePreview()
    }

    fun onNotesChanged(text: String) {
        _notes.value = text
    }

    fun onAttachmentChanged(uri: String?) {
        _attachmentUri.value = uri
    }

    private fun updatePreview() {
        previewJob?.cancel()
        val restId = uiState.value.restaurantId ?: return
        val ingId = _selectedIngredientId.value ?: return
        val areaId = _selectedAreaId.value ?: return
        val unitId = _selectedUnitOptionId.value ?: return
        val qtyText = _quantityText.value
        val parsedQty = DecimalParser.parse(qtyText) ?: return
        if (parsedQty <= BigDecimal.ZERO) {
            _preview.value = null
            return
        }

        val effAt = _effectiveAt.value

        previewJob = viewModelScope.launch {
            _isLoadingPreview.value = true
            try {
                val preview = previewWasteUseCase(
                    restaurantId = restId,
                    ingredientId = ingId,
                    areaId = areaId,
                    unitOptionId = unitId,
                    quantityEntered = parsedQty,
                    effectiveAt = effAt
                )
                _preview.value = preview
            } catch (e: Exception) {
                _preview.value = null
                _error.value = e
            } finally {
                _isLoadingPreview.value = false
            }
        }
    }

    fun onSave() {
        val state = uiState.value
        if (!state.canSave) return

        val restId = state.restaurantId ?: return
        val ingId = state.selectedIngredientId ?: return
        val areaId = state.selectedAreaId ?: return
        val unitId = state.selectedUnitOptionId ?: return
        val reason = state.selectedReason ?: return
        val qty = DecimalParser.parse(state.quantityText) ?: return
        
        _isSaving.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val finalId = if (wasteEventId == null) {
                    createWasteDraftUseCase(
                        CreateWasteDraftCommand(
                            restaurantId = restId,
                            ingredientId = ingId,
                            areaId = areaId,
                            ingredientUnitOptionId = unitId,
                            quantityEntered = qty,
                            reason = reason,
                            effectiveAt = state.effectiveAt,
                            notes = state.notes.ifBlank { null },
                            attachmentUri = state.attachmentUri
                        )
                    )
                } else {
                    updateWasteDraftUseCase(
                        UpdateWasteDraftCommand(
                            wasteEventId = wasteEventId,
                            ingredientId = ingId,
                            areaId = areaId,
                            ingredientUnitOptionId = unitId,
                            quantityEntered = qty,
                            reason = reason,
                            effectiveAt = state.effectiveAt,
                            notes = state.notes.ifBlank { null },
                            attachmentUri = state.attachmentUri
                        )
                    )
                    wasteEventId
                }
                _events.emit(WasteFormEvent.Success(finalId))
            } catch (e: Exception) {
                _error.value = e
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
