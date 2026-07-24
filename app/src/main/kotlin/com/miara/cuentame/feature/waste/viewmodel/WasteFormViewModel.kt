package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class WasteIngredientOptionUi(
    val id: IngredientId,
    val label: String,
    val isActive: Boolean,
    val isSelected: Boolean,
    val isSelectable: Boolean
)

data class WasteAreaOptionUi(
    val id: InventoryAreaId,
    val label: String,
    val isActive: Boolean,
    val isSelected: Boolean,
    val isSelectable: Boolean
)

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
    data object SetupRequired : WasteFormScreenState
    data object Immutable : WasteFormScreenState
    data class Error(val throwable: Throwable) : WasteFormScreenState
}

data class WasteFormUiState(
    val screenState: WasteFormScreenState = WasteFormScreenState.Loading,
    val isSaving: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val wasteEventId: WasteEventId? = null,
    val restaurantId: RestaurantId? = null,
    val currencyCode: String = "USD",
    val selectedIngredientId: IngredientId? = null,
    val selectedAreaId: InventoryAreaId? = null,
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val quantityText: String = "",
    val selectedReason: WasteReason? = null,
    val effectiveAt: Instant = Instant.now(),
    val notes: String = "",
    val attachmentUri: String? = null,
    val preview: WastePreview? = null,
    val ingredients: List<WasteIngredientOptionUi> = emptyList(),
    val areas: List<WasteAreaOptionUi> = emptyList(),
    val unitOptions: List<WasteUnitOptionUi> = emptyList(),
    val error: Throwable? = null,
    val canSave: Boolean = false
)

private data class WastePreviewRequest(
    val revision: Long,
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val unitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val effectiveAt: Instant
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
    private val previewWasteUseCase: PreviewWasteUseCase,
    private val attachmentPermissionManager: LocalAttachmentPermissionManager
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
    private val _previewError = MutableStateFlow<Throwable?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)
    private val _screenStateOverride = MutableStateFlow<WasteFormScreenState?>(null)
    private val _previewRevision = MutableStateFlow(0L)

    private val _events = MutableSharedFlow<WasteFormEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _unitOptions = _selectedIngredientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else flowOf(ingredientRepository.getUnitOptions(id, true))
    }.onEach { options ->
        val currentUnit = _selectedUnitOptionId.value
        if (currentUnit == null || options.none { it.id == currentUnit }) {
            val defaultOption = options.find { it.isDefaultCount && it.isActive }
                ?: options.find { it.isBase && it.isActive }
            _selectedUnitOptionId.value = defaultOption?.id
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<WasteFormUiState> = restaurantRepository.observeRestaurant()
        .flatMapLatest { restaurant ->
            if (restaurant == null) {
                return@flatMapLatest flowOf(WasteFormUiState(screenState = WasteFormScreenState.SetupRequired))
            }
            combine(
                if (wasteEventId != null) wasteRepository.observeWasteEvent(wasteEventId) else flowOf(null),
                ingredientRepository.observeIngredients(restaurant.id, true),
                areaRepository.observeAllAreas(),
                _unitOptions,
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
                _previewError,
                _hasLoadedOnce,
                _screenStateOverride
            ) { args ->
                val existingDetails = args[0] as com.miara.cuentame.core.domain.repository.WasteDetails?
                val allIngredients = args[1] as List<Ingredient>
                val allAreas = args[2] as List<InventoryArea>
                val allUnitOptions = args[3] as List<com.miara.cuentame.core.model.ingredient.IngredientUnitOption>
                val selIngId = args[4] as IngredientId?
                val selAreaId = args[5] as InventoryAreaId?
                val selUnitId = args[6] as IngredientUnitOptionId?
                val qtyText = args[7] as String
                val reason = args[8] as WasteReason?
                val effAt = args[9] as Instant
                val notes = args[10] as String
                val attUri = args[11] as String?
                val preview = args[12] as WastePreview?
                val isSaving = args[13] as Boolean
                val isLoadingPreview = args[14] as Boolean
                val error = args[15] as Throwable?
                val previewError = args[16] as Throwable?
                val hasLoadedOnce = args[17] as Boolean
                val screenStateOverride = args[18] as WasteFormScreenState?

                val isHydrated = if (wasteEventId != null) hasLoadedOnce else true

                val missingReference = isHydrated && wasteEventId != null && (
                    (selIngId != null && allIngredients.none { it.id == selIngId }) ||
                    (selAreaId != null && allAreas.none { it.id == selAreaId }) ||
                    (selUnitId != null && allUnitOptions.none { it.id == selUnitId })
                )

                val screenState = when {
                    screenStateOverride != null -> screenStateOverride
                    missingReference -> WasteFormScreenState.Error(ValidationError.RecordNotFound)
                    !hasLoadedOnce && wasteEventId != null && error == null -> WasteFormScreenState.Loading
                    error != null && wasteEventId != null && existingDetails == null -> WasteFormScreenState.Error(error)
                    wasteEventId != null && existingDetails == null -> WasteFormScreenState.NotFound
                    wasteEventId != null && existingDetails?.event?.restaurantId != restaurant.id -> WasteFormScreenState.OwnershipMismatch
                    else -> WasteFormScreenState.Ready
                }

                val ingredientOptions = allIngredients
                    .filter { it.isActive || it.id == selIngId }
                    .map { ing ->
                        WasteIngredientOptionUi(
                            id = ing.id,
                            label = ing.name,
                            isActive = ing.isActive,
                            isSelected = ing.id == selIngId,
                            isSelectable = ing.isActive || ing.id == selIngId
                        )
                    }

                val areaOptions = allAreas
                    .filter { it.isActive || it.id == selAreaId }
                    .map { area ->
                        WasteAreaOptionUi(
                            id = area.id,
                            label = area.name,
                            isActive = area.isActive,
                            isSelected = area.id == selAreaId,
                            isSelectable = area.isActive || area.id == selAreaId
                        )
                    }

                val unitOptionsUi = allUnitOptions
                    .filter { it.isActive || it.id == selUnitId }
                    .map { opt ->
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

                val parsedQty = DecimalParser.parse(qtyText)
                val canSave = selIngId != null && selAreaId != null && selUnitId != null && 
                              reason != null && parsedQty != null && parsedQty > BigDecimal.ZERO &&
                              !isSaving && !missingReference &&
                              ingredientOptions.any { it.id == selIngId } &&
                              areaOptions.any { it.id == selAreaId } &&
                              unitOptionsUi.any { it.id == selUnitId }

                WasteFormUiState(
                    screenState = screenState,
                    isSaving = isSaving,
                    isLoadingPreview = isLoadingPreview,
                    wasteEventId = wasteEventId,
                    restaurantId = restaurant.id,
                    currencyCode = restaurant.currencyCode,
                    selectedIngredientId = selIngId,
                    selectedAreaId = selAreaId,
                    selectedUnitOptionId = selUnitId,
                    quantityText = qtyText,
                    selectedReason = reason,
                    effectiveAt = effAt,
                    notes = notes,
                    attachmentUri = attUri,
                    preview = preview,
                    ingredients = ingredientOptions,
                    areas = areaOptions,
                    unitOptions = unitOptionsUi,
                    error = error ?: previewError,
                    canSave = canSave
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WasteFormUiState()
        )

    init {
        viewModelScope.launch {
            combine(
                _selectedIngredientId,
                _selectedAreaId,
                _selectedUnitOptionId,
                _quantityText,
                _effectiveAt,
                _previewRevision
            ) { args ->
                val selIngId = args[0] as IngredientId?
                val selAreaId = args[1] as InventoryAreaId?
                val selUnitId = args[2] as IngredientUnitOptionId?
                val qtyText = args[3] as String
                val effAt = args[4] as Instant
                val revision = args[5] as Long

                val restId = restaurantRepository.getRestaurant()?.id
                if (restId == null || selIngId == null || selAreaId == null || selUnitId == null || qtyText.isBlank()) {
                    return@combine null
                }
                val parsedQty = DecimalParser.parse(qtyText)
                if (parsedQty == null || parsedQty <= BigDecimal.ZERO) {
                    return@combine null
                }
                WastePreviewRequest(revision, restId, selIngId, selAreaId, selUnitId, parsedQty, effAt)
            }.collectLatest { request ->
                if (request == null) {
                    _preview.value = null
                    _isLoadingPreview.value = false
                    _previewError.value = null
                    return@collectLatest
                }

                _isLoadingPreview.value = true
                _preview.value = null
                _previewError.value = null
                try {
                    val result = previewWasteUseCase(
                        restaurantId = request.restaurantId,
                        ingredientId = request.ingredientId,
                        areaId = request.areaId,
                        unitOptionId = request.unitOptionId,
                        quantityEntered = request.quantityEntered,
                        effectiveAt = request.effectiveAt
                    )
                    if (_previewRevision.value == request.revision) {
                        _preview.value = result
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_previewRevision.value == request.revision) {
                        _previewError.value = e
                    }
                } finally {
                    if (_previewRevision.value == request.revision) {
                        _isLoadingPreview.value = false
                    }
                }
            }
        }

        if (wasteEventIdStr != null && wasteEventIdStr.isBlank()) {
            _screenStateOverride.value = WasteFormScreenState.InvalidRoute
        } else if (wasteEventId != null) {
            viewModelScope.launch {
                try {
                    val existing = wasteRepository.getById(wasteEventId)
                    if (existing != null) {
                        if (existing.status != DocumentStatus.DRAFT) {
                            _screenStateOverride.value = WasteFormScreenState.Immutable
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
                    } else {
                        _screenStateOverride.value = WasteFormScreenState.NotFound
                    }
                } catch (e: Exception) {
                    _error.value = e
                } finally {
                    _hasLoadedOnce.value = true
                }
            }
        } else {
            _hasLoadedOnce.value = true
        }
    }

    fun onIngredientSelected(id: IngredientId) {
        if (_selectedIngredientId.value == id) return
        _selectedIngredientId.value = id
        _selectedUnitOptionId.value = null
        _preview.value = null
        _previewRevision.value++
    }

    fun onAreaSelected(id: InventoryAreaId) {
        if (_selectedAreaId.value == id) return
        _selectedAreaId.value = id
        _preview.value = null
        _previewRevision.value++
    }

    fun onUnitOptionSelected(id: IngredientUnitOptionId) {
        if (_selectedUnitOptionId.value == id) return
        _selectedUnitOptionId.value = id
        _preview.value = null
        _previewRevision.value++
    }

    fun onQuantityChanged(text: String) {
        if (_quantityText.value == text) return
        _quantityText.value = text
        _preview.value = null
        _previewRevision.value++
    }

    fun onReasonSelected(reason: WasteReason) {
        _selectedReason.value = reason
    }

    fun onEffectiveAtChanged(instant: Instant) {
        if (_effectiveAt.value == instant) return
        _effectiveAt.value = instant
        _preview.value = null
        _previewRevision.value++
    }

    fun onNotesChanged(text: String) {
        _notes.value = text
    }

    fun onAttachmentChanged(uri: String?) {
        if (uri == null) {
            _attachmentUri.value = null
            return
        }
        
        try {
            val parsedUri = android.net.Uri.parse(uri)
            val result = attachmentPermissionManager.persistReadPermission(parsedUri)
            if (result.isSuccess) {
                _attachmentUri.value = uri
            } else {
                _error.value = result.exceptionOrNull()
            }
        } catch (e: Exception) {
            _error.value = e
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
        _previewError.value = null
    }
}
