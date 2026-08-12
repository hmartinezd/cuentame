package com.miara.cuentame.feature.counts.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.StockCountLineId
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.model.count.ArchivedCountCandidate
import com.miara.cuentame.core.domain.model.count.CountCandidateResult
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.domain.repository.StockCountAreaDetails
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.usecase.GetMissingCountItemsUseCase
import com.miara.cuentame.core.domain.usecase.PreviewStockCountLineUseCase
import com.miara.cuentame.core.domain.usecase.StockCountLinePreview
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.StockCountStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class CountUnitOptionUi(
    val id: IngredientUnitOptionId,
    val label: String,
    val factorToBase: BigDecimal,
    val isSelected: Boolean,
    val isSelectable: Boolean,
    val isActive: Boolean
)

data class StockCountLineEntry(
    val ingredientId: String,
    val ingredientName: String,
    val categoryName: String?,
    val unitId: String,
    val unitName: String,
    val factorToBase: BigDecimal,
    val baseUnitName: String?,
    val quantityText: String = "",
    val lineId: String? = null,
    val persistedPreview: StockCountLinePreview? = null,
    val persistedUpdatedAt: java.time.Instant? = null,
    val editingPreview: StockCountLinePreview? = null,
    val hasUserEdit: Boolean = false,
    val editRevision: Long = 0,
    val savedRevision: Long = -1,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: Throwable? = null,
    val unitOptions: List<CountUnitOptionUi> = emptyList()
) {
    val isSaved: Boolean = hasUserEdit && editRevision == savedRevision && error == null && !isSaving && !isDeleting
    val isPending: Boolean = hasUserEdit && (editRevision > savedRevision || isSaving)
    val hasPersistedObservation: Boolean = lineId != null
    val hasValidCurrentObservation: Boolean
        get() = quantityText.isNotBlank() && DecimalParser.parse(quantityText)?.let { it >= BigDecimal.ZERO } == true
    val isCountedForProgress: Boolean = hasValidCurrentObservation && (isPending || isSaved || hasPersistedObservation)
    val isCounted: Boolean get() = isCountedForProgress
    val preview: StockCountLinePreview?
        get() = if (isPending) editingPreview else persistedPreview
}

enum class StockCountItemFilter { ALL, UNCOUNTED, COUNTED }

sealed interface StockCountAreaScreenState {
    data object Loading : StockCountAreaScreenState
    data object Ready : StockCountAreaScreenState
    data object NotFound : StockCountAreaScreenState
    data object InvalidRoute : StockCountAreaScreenState
    data object OwnershipMismatch : StockCountAreaScreenState
    data class Error(val throwable: Throwable) : StockCountAreaScreenState
}

data class StockCountAreaUiState(
    val screenState: StockCountAreaScreenState = StockCountAreaScreenState.Loading,
    val isCompleting: Boolean = false,
    val hasPendingSaves: Boolean = false,
    val searchQuery: String = "",
    val itemFilter: StockCountItemFilter = StockCountItemFilter.ALL,
    val details: StockCountAreaDetails? = null,
    val lineEntries: List<StockCountLineEntry> = emptyList(),
    val searchResults: List<Ingredient> = emptyList(),
    val archivedWarnings: List<ArchivedCountCandidate> = emptyList(),
    val missingCount: Int = 0,
    val deletingIngredientId: String? = null,
    val error: Throwable? = null,
    val canEdit: Boolean = false,
    val canReopen: Boolean = false,
    val countedItemCount: Int = 0,
    val totalCountableItemCount: Int = 0,
    val isEditingOrder: Boolean = false
)

sealed interface StockCountAreaEvent {
    data object AreaCompleted : StockCountAreaEvent
    data object NavigateBack : StockCountAreaEvent
    data class LineDeleted(val ingredientId: String) : StockCountAreaEvent
    data class ShowError(val error: Throwable) : StockCountAreaEvent
    data class FocusQuantity(val ingredientId: String) : StockCountAreaEvent
    data object ImeDone : StockCountAreaEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockCountAreaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockCountRepository,
    private val restaurantRepository: RestaurantRepository,
    private val getMissingItemsUseCase: GetMissingCountItemsUseCase,
    private val previewUseCase: PreviewStockCountLineUseCase,
    private val ingredientRepository: IngredientRepository,
    private val categoryRepository: IngredientCategoryRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val countIdStr: String? = savedStateHandle["countId"]
    private val countAreaIdStr: String? = savedStateHandle["countAreaId"]
    
    private val countId = if (!countIdStr.isNullOrBlank()) StockCountId(countIdStr) else null
    private val countAreaId = if (!countAreaIdStr.isNullOrBlank()) StockCountAreaId(countAreaIdStr) else null

    private val _searchQuery = MutableStateFlow("")
    private val _itemFilter = MutableStateFlow(StockCountItemFilter.ALL)
    private val _orderedIngredientIds = MutableStateFlow<List<String>>(emptyList())
    private val _isEditingOrder = MutableStateFlow(false)
    private val _lineEntries = MutableStateFlow<Map<String, StockCountLineEntry>>(emptyMap())
    private val _archivedWarnings = MutableStateFlow<List<ArchivedCountCandidate>>(emptyList())
    private val _missingActiveCandidates = MutableStateFlow<List<Ingredient>>(emptyList())
    private val _isCompleting = MutableStateFlow(false)
    private val _deletingIngredientId = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _events = Channel<StockCountAreaEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val debounceJobs = mutableMapOf<String, Job>()
    private val previewJobs = mutableMapOf<String, Job>()
    private val coordinators = mutableMapOf<String, LineCoordinator>()

    private data class CombinedOtherStates(
        val completing: Boolean,
        val deletingId: String?,
        val error: Throwable?,
        val searchResults: List<Ingredient>,
        val archivedWarnings: List<ArchivedCountCandidate>,
        val missingCandidates: List<Ingredient>,
        val hasLoadedOnce: Boolean,
        val itemFilter: StockCountItemFilter,
        val orderedIds: List<String>,
        val isEditingOrder: Boolean
    )

    private val searchResultsFlow = _searchQuery.flatMapLatest { query ->
        if (query.length < 2 || countAreaId == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else {
            repository.observeCountArea(countAreaId).filterNotNull().flatMapLatest { details ->
                ingredientRepository.observeIngredients(details.restaurantId, false)
                    .map { ingredients ->
                        ingredients.filter { it.name.contains(query, ignoreCase = true) }
                    }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<StockCountAreaUiState> = combine(
        if (countAreaId != null) repository.observeCountArea(countAreaId) else kotlinx.coroutines.flow.flowOf(null),
        restaurantRepository.observeRestaurant(),
        _lineEntries,
        _searchQuery,
        combine(
            _isCompleting,
            _deletingIngredientId,
            _error,
            searchResultsFlow,
            _archivedWarnings,
            _missingActiveCandidates,
            _hasLoadedOnce,
            _itemFilter,
            _orderedIngredientIds,
            _isEditingOrder
        ) { args ->
            CombinedOtherStates(
                completing = args[0] as Boolean,
                deletingId = args[1] as String?,
                error = args[2] as Throwable?,
                searchResults = args[3] as List<Ingredient>,
                archivedWarnings = args[4] as List<ArchivedCountCandidate>,
                missingCandidates = args[5] as List<Ingredient>,
                hasLoadedOnce = args[6] as Boolean,
                itemFilter = args[7] as StockCountItemFilter,
                orderedIds = args[8] as List<String>,
                isEditingOrder = args[9] as Boolean
            )
        }
    ) { details, activeRestaurant, entriesMap, query, others ->
        val screenState = when {
            countId == null || countAreaId == null -> 
                StockCountAreaScreenState.InvalidRoute
            !others.hasLoadedOnce && others.error == null -> StockCountAreaScreenState.Loading
            others.error != null && details == null -> StockCountAreaScreenState.Error(others.error)
            details == null -> StockCountAreaScreenState.NotFound
            activeRestaurant == null || details.restaurantId != activeRestaurant.id -> StockCountAreaScreenState.OwnershipMismatch
            details.area.stockCountId != countId -> StockCountAreaScreenState.OwnershipMismatch
            else -> StockCountAreaScreenState.Ready
        }

        val orderIndex = others.orderedIds.withIndex().associate { it.value to it.index }
        val sortedEntries = entriesMap.values.sortedWith(
            compareBy<StockCountLineEntry> { orderIndex[it.ingredientId] ?: Int.MAX_VALUE }
                .thenBy { it.ingredientName.lowercase() }
                .thenBy { it.ingredientId }
        )
        val countStatus = details?.countStatus ?: StockCountStatus.DRAFT
        val areaStatus = details?.area?.status ?: CountAreaStatus.NOT_STARTED

        StockCountAreaUiState(
            screenState = screenState,
            isCompleting = others.completing,
            hasPendingSaves = entriesMap.values.any { it.isPending },
            searchQuery = query,
            itemFilter = others.itemFilter,
            details = details,
            lineEntries = sortedEntries.filter { entry ->
                entry.ingredientName.contains(query, ignoreCase = true) && when (others.itemFilter) {
                    StockCountItemFilter.ALL -> true
                    StockCountItemFilter.UNCOUNTED -> !entry.isCountedForProgress
                    StockCountItemFilter.COUNTED -> entry.isCountedForProgress
                }
            },
            searchResults = others.searchResults.filter { !entriesMap.containsKey(it.id.value) },
            archivedWarnings = others.archivedWarnings,
            missingCount = others.archivedWarnings.size + others.missingCandidates.size,
            deletingIngredientId = others.deletingId,
            error = others.error,
            canEdit = countStatus == StockCountStatus.DRAFT && areaStatus != CountAreaStatus.COMPLETED,
            canReopen = countStatus == StockCountStatus.DRAFT && areaStatus == CountAreaStatus.COMPLETED,
            countedItemCount = entriesMap.values.count { it.isCountedForProgress },
            totalCountableItemCount = entriesMap.size,
            isEditingOrder = others.isEditingOrder
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockCountAreaUiState()
    )

    init {
        if (countAreaId != null) {
            viewModelScope.launch {
                repository.observeCountArea(countAreaId)
                    .onEach { _hasLoadedOnce.value = true }
                    .collect { details ->
                        if (details != null && _lineEntries.value.isEmpty()) {
                            initializeEntries(details)
                        } else if (details != null) {
                            updateLineIds(details)
                        }
                    }
            }
        }
    }

    private suspend fun initializeEntries(details: StockCountAreaDetails) {
        val cid = countId ?: return
        val aid = details.area.areaId
        val candidateResult = getMissingItemsUseCase(
            restaurantId = details.restaurantId,
            countId = cid,
            areaId = aid,
            effectiveAt = details.effectiveAt
        )

        _orderedIngredientIds.value = repository.getItemOrder(details.area.areaId).map { it.value }
        val entries = mutableMapOf<String, StockCountLineEntry>()
        val isEditable = details.countStatus == StockCountStatus.DRAFT && details.area.status != CountAreaStatus.COMPLETED

        details.lines.forEach { line ->
            val ingredient = ingredientRepository.getById(line.ingredientId) ?: return@forEach
            val options = ingredientRepository.getUnitOptions(line.ingredientId, true)
            val option = options.find { it.id == line.ingredientUnitOptionId } ?: return@forEach
            val category = ingredient.categoryId?.let { categoryRepository.getById(it) }
            val baseUnit = options.find { it.isBase }

            entries[line.ingredientId.value] = StockCountLineEntry(
                ingredientId = line.ingredientId.value,
                ingredientName = ingredient.name,
                categoryName = category?.name,
                unitId = line.ingredientUnitOptionId.value,
                unitName = option.shortLabel,
                factorToBase = option.factorToBase,
                baseUnitName = baseUnit?.shortLabel,
                quantityText = line.quantityEntered.toPlainString(),
                lineId = line.id.value,
                hasUserEdit = true,
                editRevision = 0,
                savedRevision = 0,
                unitOptions = mapToUnitUi(options, line.ingredientUnitOptionId, isEditable),
                persistedPreview = StockCountLinePreview(
                    countedQuantityBase = line.quantityBase,
                    expectedQuantityBase = line.expectedQuantityBaseSnapshot,
                    provisionalAdjustmentBase = line.adjustmentQuantityBase
                        ?: line.quantityBase.subtract(line.expectedQuantityBaseSnapshot ?: BigDecimal.ZERO),
                    willCreateOpeningBalance = line.expectedQuantityBaseSnapshot == null,
                    averageCostBase = null,
                    estimatedValueChange = null
                ),
                persistedUpdatedAt = line.updatedAt
            )
            getCoordinator(line.ingredientId.value, line.id.value)
        }

        if (isEditable) {
            candidateResult.missingActiveCandidates.forEach { ingredient ->
                if (!entries.containsKey(ingredient.id.value)) {
                    val options = ingredientRepository.getUnitOptions(ingredient.id, true)
                    val option = options.find { it.isDefaultCount } ?: options.find { it.isBase } ?: return@forEach
                    val category = ingredient.categoryId?.let { categoryRepository.getById(it) }

                    entries[ingredient.id.value] = StockCountLineEntry(
                        ingredientId = ingredient.id.value,
                        ingredientName = ingredient.name,
                        categoryName = category?.name,
                        unitId = option.id.value,
                        unitName = option.shortLabel,
                        factorToBase = option.factorToBase,
                        baseUnitName = options.find { it.isBase }?.shortLabel,
                        quantityText = "",
                        hasUserEdit = false,
                        unitOptions = mapToUnitUi(options, option.id, isEditable)
                    )
                    getCoordinator(ingredient.id.value)
                }
            }
        }

        _lineEntries.value = entries
        _archivedWarnings.value = candidateResult.archivedBalanceWarnings
        _missingActiveCandidates.value = candidateResult.missingActiveCandidates
    }

    private fun mapToUnitUi(
        options: List<IngredientUnitOption>,
        selectedId: IngredientUnitOptionId,
        isEditable: Boolean
    ): List<CountUnitOptionUi> {
        return options.map { opt ->
            val isCurrentSelection = opt.id == selectedId
            CountUnitOptionUi(
                id = opt.id,
                label = opt.shortLabel,
                factorToBase = opt.factorToBase,
                isSelected = isCurrentSelection,
                isSelectable = isEditable && (opt.isActive || isCurrentSelection) && opt.factorToBase > BigDecimal.ZERO,
                isActive = opt.isActive
            )
        }
    }

    private fun updateLineIds(details: StockCountAreaDetails) {
        _lineEntries.update { entries ->
            entries.mapValues { (id, entry) ->
                val line = details.lines.find { it.ingredientId.value == id }
                if (line != null) {
                    val coordinator = coordinators[id]
                    if (coordinator != null) coordinator.syncLineId(line.id.value)
                    val persistedPreview = StockCountLinePreview(
                        countedQuantityBase = line.quantityBase,
                        expectedQuantityBase = line.expectedQuantityBaseSnapshot,
                        provisionalAdjustmentBase = line.adjustmentQuantityBase ?: line.quantityBase.subtract(line.expectedQuantityBaseSnapshot ?: BigDecimal.ZERO),
                        willCreateOpeningBalance = line.expectedQuantityBaseSnapshot == null,
                        averageCostBase = null,
                        estimatedValueChange = null
                    )
                    if (entry.persistedUpdatedAt == null || line.updatedAt > entry.persistedUpdatedAt) {
                        entry.copy(
                            lineId = line.id.value,
                            persistedPreview = persistedPreview,
                            persistedUpdatedAt = line.updatedAt
                        )
                    } else {
                        entry.copy(lineId = line.id.value)
                    }
                } else entry
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onItemFilterChanged(filter: StockCountItemFilter) {
        _itemFilter.value = filter
    }

    fun onToggleOrderEditing() {
        val entering = !_isEditingOrder.value
        if (entering) {
            _searchQuery.value = ""
            _itemFilter.value = StockCountItemFilter.ALL
        }
        _isEditingOrder.value = entering
    }

    fun onMoveItem(ingredientId: String, direction: Int) {
        val base = uiState.value.lineEntries.map { it.ingredientId }.toMutableList()
        val from = base.indexOf(ingredientId)
        val to = (from + direction).coerceIn(0, base.lastIndex)
        if (from < 0 || from == to) return
        base.add(to, base.removeAt(from))
        _orderedIngredientIds.value = base
        val areaId = uiState.value.details?.area?.areaId ?: return
        viewModelScope.launch {
            try {
                repository.saveItemOrder(areaId, base.map(::IngredientId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun onImeAction(ingredientId: String) {
        val visible = uiState.value.lineEntries
        val index = visible.indexOfFirst { it.ingredientId == ingredientId }
        if (index < 0) return
        val entry = visible[index]
        val parsed = DecimalParser.parse(entry.quantityText)
        if (parsed == null || parsed < BigDecimal.ZERO) {
            val error = if (parsed != null) ValidationError.InvalidCountQuantity else ValidationError.InvalidDecimal
            _lineEntries.update { it + (ingredientId to entry.copy(error = error)) }
            return
        }
        viewModelScope.launch {
            debounceJobs[ingredientId]?.cancel()
            val completion = CompletableDeferred<Boolean>()
            getCoordinator(ingredientId).enqueueFlush(completion)
            if (completion.await()) {
                val next = visible.getOrNull(index + 1)
                if (next != null) _events.send(StockCountAreaEvent.FocusQuantity(next.ingredientId))
                else _events.send(StockCountAreaEvent.ImeDone)
            }
        }
    }

    fun onAddIngredient(ingredient: Ingredient) {
        if (_lineEntries.value.containsKey(ingredient.id.value)) return

        viewModelScope.launch {
            val options = ingredientRepository.getUnitOptions(ingredient.id, true)
            val option = options.find { it.isDefaultCount } ?: options.find { it.isBase } ?: return@launch
            val category = ingredient.categoryId?.let { categoryRepository.getById(it) }

            val entry = StockCountLineEntry(
                ingredientId = ingredient.id.value,
                ingredientName = ingredient.name,
                categoryName = category?.name,
                unitId = option.id.value,
                unitName = option.shortLabel,
                factorToBase = option.factorToBase,
                baseUnitName = options.find { it.isBase }?.shortLabel,
                quantityText = "",
                hasUserEdit = false,
                unitOptions = mapToUnitUi(options, option.id, true)
            )
            _lineEntries.update { it + (ingredient.id.value to entry) }
            getCoordinator(ingredient.id.value)
            updatePreview(entry)
            _searchQuery.value = ""
        }
    }

    fun onQuantityChanged(ingredientId: String, quantity: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        if (entry.quantityText == quantity || entry.isDeleting) return

        val newRevision = entry.editRevision + 1
        val updatedEntry = entry.copy(
            quantityText = quantity,
            editRevision = newRevision,
            hasUserEdit = true,
            error = null
        )
        _lineEntries.update { it + (ingredientId to updatedEntry) }

        if (quantity.isBlank() && entry.lineId != null) {
            debounceJobs[ingredientId]?.cancel()
            getCoordinator(ingredientId, entry.lineId).enqueueClear()
            return
        }

        updatePreview(updatedEntry)

        debounceJobs[ingredientId]?.cancel()
        val parsed = DecimalParser.parse(quantity)
        if (quantity.isNotBlank() && parsed != null && parsed >= BigDecimal.ZERO) {
            debounceJobs[ingredientId] = viewModelScope.launch {
                delay(500)
                getCoordinator(ingredientId).enqueueSave(newRevision)
            }
        } else if (quantity.isNotBlank()) {
             val error = if (parsed != null && parsed < BigDecimal.ZERO) ValidationError.InvalidCountQuantity else ValidationError.InvalidDecimal
             _lineEntries.update { it + (ingredientId to updatedEntry.copy(error = error)) }
        }
    }

    fun onUnitChanged(ingredientId: String, optionId: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        if (entry.unitId == optionId || entry.isDeleting) return
        
        val unitUi = entry.unitOptions.find { it.id.value == optionId } ?: return
        if (!unitUi.isSelectable) return

        val newRevision = entry.editRevision + 1
        
        val newUnitOptions = entry.unitOptions.map { opt ->
            val isSelected = opt.id.value == optionId
            opt.copy(
                isSelected = isSelected,
                isSelectable = (opt.isActive || isSelected) && opt.factorToBase > BigDecimal.ZERO
            )
        }

        val updatedEntry = entry.copy(
            unitId = optionId,
            unitName = unitUi.label,
            factorToBase = unitUi.factorToBase,
            editRevision = newRevision,
            hasUserEdit = true,
            error = null,
            unitOptions = newUnitOptions
        )
        
        _lineEntries.update { it + (ingredientId to updatedEntry) }
        updatePreview(updatedEntry)
        
        debounceJobs[ingredientId]?.cancel()
        val parsed = DecimalParser.parse(updatedEntry.quantityText)
        if (updatedEntry.quantityText.isNotBlank() && parsed != null && parsed >= BigDecimal.ZERO) {
            getCoordinator(ingredientId).enqueueSave(newRevision)
        }
    }

    fun onConfirmDelete(ingredientId: String) {
        val coordinator = coordinators[ingredientId] ?: run {
            _deletingIngredientId.value = null
            return
        }
        
        if (_deletingIngredientId.value != null) return
        _deletingIngredientId.value = ingredientId
        
        _lineEntries.update { entries ->
            val item = entries[ingredientId]
            if (item != null) entries + (ingredientId to item.copy(isDeleting = true)) else entries
        }
        
        debounceJobs[ingredientId]?.cancel()
        previewJobs[ingredientId]?.cancel()

        val completion = CompletableDeferred<Unit>()
        coordinator.enqueueDelete(completion)

        viewModelScope.launch {
            try {
                completion.await()
            } catch (e: Exception) {
                // error handled in loop
            } finally {
                _deletingIngredientId.value = null
            }
        }
    }

    private fun updateMissingCountState() {
        viewModelScope.launch {
            val details = uiState.value.details ?: return@launch
            val result = getMissingItemsUseCase(details.restaurantId, countId!!, details.area.areaId, details.effectiveAt)
            _missingActiveCandidates.value = result.missingActiveCandidates
            _archivedWarnings.value = result.archivedBalanceWarnings
        }
    }

    private fun updatePreview(entry: StockCountLineEntry) {
        previewJobs[entry.ingredientId]?.cancel()
        val parsed = DecimalParser.parse(entry.quantityText)
        if (parsed == null || parsed < BigDecimal.ZERO) {
            _lineEntries.update { entries ->
                val current = entries[entry.ingredientId]
                if (current != null && current.editRevision == entry.editRevision) {
                    entries + (entry.ingredientId to current.copy(editingPreview = null))
                } else entries
            }
            return
        }

        val details = uiState.value.details ?: return
        val revision = entry.editRevision
        
        previewJobs[entry.ingredientId] = viewModelScope.launch {
            try {
                val preview = previewUseCase(
                    restaurantId = details.restaurantId,
                    ingredientId = IngredientId(entry.ingredientId),
                    areaId = details.area.areaId,
                    effectiveAt = timeProvider.now(),
                    quantityBase = parsed.multiply(entry.factorToBase, MathContext.DECIMAL128)
                )
                _lineEntries.update { entries ->
                    val current = entries[entry.ingredientId]
                    if (current != null && current.editRevision == revision) {
                        entries + (entry.ingredientId to current.copy(editingPreview = preview))
                    } else entries
                }
            } catch (e: Exception) {
            }
        }
    }

    suspend fun flushPendingSaves(): Boolean {
        val entriesToFlush = _lineEntries.value.values.filter { it.isPending && !it.isDeleting }
        if (entriesToFlush.isEmpty()) return true

        entriesToFlush.forEach { debounceJobs[it.ingredientId]?.cancel() }

        if (entriesToFlush.any { 
            val parsed = DecimalParser.parse(it.quantityText)
            parsed == null || parsed < BigDecimal.ZERO 
        }) return false

        val completions = entriesToFlush.map { entry ->
            val deferred = CompletableDeferred<Boolean>()
            getCoordinator(entry.ingredientId).enqueueFlush(deferred)
            deferred
        }
        
        completions.awaitAll()
        
        val finalState = _lineEntries.value
        return entriesToFlush.none { 
            val entry = finalState[it.ingredientId]
            entry == null || entry.isPending || entry.error != null 
        }
    }

    fun onBackRequested() {
        val state = uiState.value
        if (!state.canEdit) {
            viewModelScope.launch { _events.send(StockCountAreaEvent.NavigateBack) }
            return
        }

        viewModelScope.launch {
            if (flushPendingSaves()) {
                _events.send(StockCountAreaEvent.NavigateBack)
            } else {
                _error.value = ValidationError.PendingCountSaves
            }
        }
    }

    fun onCompleteArea() {
        val cid = countId ?: return
        val aid = countAreaId ?: return
        if (_isCompleting.value) return
        
        _isCompleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                if (flushPendingSaves()) {
                    repository.completeArea(cid, aid)
                    _isCompleting.value = false
                    _events.send(StockCountAreaEvent.AreaCompleted)
                } else {
                    _isCompleting.value = false
                    _error.value = ValidationError.PendingCountSaves
                }
            } catch (e: Exception) {
                _isCompleting.value = false
                _error.value = e
            }
        }
    }

    fun onReopenArea() {
        val cid = countId ?: return
        val aid = countAreaId ?: return
        viewModelScope.launch {
            try {
                repository.reopenArea(cid, aid)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun getCoordinator(ingredientId: String, initialLineId: String? = null): LineCoordinator {
        return coordinators.getOrPut(ingredientId) {
            LineCoordinator(ingredientId, initialLineId).apply { start() }
        }
    }

    private sealed interface LineOperation {
        data class Save(val revision: Long) : LineOperation
        data object Clear : LineOperation
        data class Delete(val completion: CompletableDeferred<Unit>) : LineOperation
        data class Flush(val completion: CompletableDeferred<Boolean>) : LineOperation
    }

    private inner class LineCoordinator(
        val ingredientId: String,
        initialLineId: String?
    ) {
        private val channel = Channel<LineOperation>(Channel.UNLIMITED)
        private var lineId: String? = initialLineId
        private var isDeleting = false
        private var job: Job? = null

        fun start() {
            job = viewModelScope.launch {
                try {
                    for (op in channel) {
                        process(op)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Loop crash protection
                } finally {
                    coordinators.remove(ingredientId)
                    // Ensure isSaving is reset in UI if loop dies
                    _lineEntries.update { entries ->
                        val current = entries[ingredientId]
                        if (current != null) entries + (ingredientId to current.copy(isSaving = false)) else entries
                    }
                }
            }
        }

        fun syncLineId(id: String) {
            lineId = id
        }

        fun enqueueSave(revision: Long) {
            channel.trySend(LineOperation.Save(revision))
        }

        fun enqueueDelete(completion: CompletableDeferred<Unit>) {
            channel.trySend(LineOperation.Delete(completion))
        }

        fun enqueueClear() {
            channel.trySend(LineOperation.Clear)
        }

        fun enqueueFlush(completion: CompletableDeferred<Boolean>) {
            channel.trySend(LineOperation.Flush(completion))
        }

        private suspend fun process(op: LineOperation) {
            when (op) {
                is LineOperation.Save -> handleSave(op.revision)
                LineOperation.Clear -> handleClear()
                is LineOperation.Delete -> handleDelete(op.completion)
                is LineOperation.Flush -> handleFlush(op.completion)
            }
        }


        private suspend fun handleClear() {
            val existingId = lineId ?: return
            val cid = countId ?: return
            val aid = countAreaId ?: return
            try {
                repository.deleteLine(cid, aid, StockCountLineId(existingId))
                lineId = null
                _lineEntries.update { entries ->
                    val current = entries[ingredientId] ?: return@update entries
                    entries + (ingredientId to current.copy(
                        lineId = null,
                        hasUserEdit = false,
                        savedRevision = -1,
                        isSaving = false,
                        persistedPreview = null,
                        persistedUpdatedAt = null,
                        editingPreview = null,
                        error = null
                    ))
                }
                updateMissingCountState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lineEntries.update { entries ->
                    val current = entries[ingredientId] ?: return@update entries
                    entries + (ingredientId to current.copy(error = e, isSaving = false))
                }
            }
        }

        private suspend fun handleSave(revision: Long) {
            if (isDeleting) return
            
            val entry = _lineEntries.value[ingredientId] ?: return
            if (entry.editRevision != revision) return
            if (entry.savedRevision >= revision && lineId != null) return
            
            val parsed = DecimalParser.parse(entry.quantityText) ?: return
            if (parsed < BigDecimal.ZERO) return

            _lineEntries.update { entries ->
                val current = entries[ingredientId]
                if (current != null && current.editRevision == revision) {
                    entries + (ingredientId to current.copy(isSaving = true))
                } else entries
            }

            try {
                val cid = countId ?: return
                val aid = countAreaId ?: return
                
                val savedLine = repository.saveLine(
                    SaveStockCountLineCommand(
                        countId = cid,
                        countAreaId = aid,
                        lineId = lineId?.let { StockCountLineId(it) },
                        ingredientId = IngredientId(ingredientId),
                        ingredientUnitOptionId = IngredientUnitOptionId(entry.unitId),
                        quantityEntered = parsed,
                        notes = null
                    )
                )
                lineId = savedLine.id.value
                val authoritativePreview = savedLine.toPersistedPreview()
                
                _lineEntries.update { entries ->
                    val current = entries[ingredientId]
                    if (current == null) return@update entries
                    
                    val updated = if (current.editRevision == revision) {
                        current.copy(
                            isSaving = false,
                            savedRevision = revision,
                            lineId = lineId,
                            persistedPreview = authoritativePreview,
                            persistedUpdatedAt = savedLine.updatedAt,
                            editingPreview = null,
                            error = null
                        )
                    } else {
                        current.copy(
                            isSaving = false,
                            lineId = lineId,
                            persistedPreview = authoritativePreview,
                            persistedUpdatedAt = savedLine.updatedAt
                        )
                    }
                    entries + (ingredientId to updated)
                }
                updateMissingCountState()
            } catch (e: Exception) {
                 _lineEntries.update { entries ->
                    val current = entries[ingredientId]
                    if (current == null) return@update entries
                    
                    val updated = if (current.editRevision == revision) {
                        current.copy(isSaving = false, error = e)
                    } else {
                        current.copy(isSaving = false)
                    }
                    entries + (ingredientId to updated)
                }
            }
        }

        private suspend fun handleDelete(completion: CompletableDeferred<Unit>) {
            isDeleting = true
            val cid = countId
            val aid = countAreaId
            
            try {
                if (lineId != null && cid != null && aid != null) {
                    repository.deleteLine(cid, aid, StockCountLineId(lineId!!))
                }
                lineId = null
                _lineEntries.update { it - ingredientId }
                updateMissingCountState()
                _events.send(StockCountAreaEvent.LineDeleted(ingredientId))
                completion.complete(Unit)
                channel.close()
            } catch (e: Exception) {
                isDeleting = false
                _lineEntries.update { entries ->
                    val item = entries[ingredientId]
                    if (item != null) entries + (ingredientId to item.copy(isDeleting = false)) else entries
                }
                _events.send(StockCountAreaEvent.ShowError(e))
                completion.completeExceptionally(e)
            }
        }

        private suspend fun handleFlush(completion: CompletableDeferred<Boolean>) {
            if (isDeleting) {
                completion.complete(false)
                return
            }
            
            val entry = _lineEntries.value[ingredientId]
            if (entry == null) {
                completion.complete(true)
                return
            }
            
            if (entry.isPending) {
                handleSave(entry.editRevision)
            }
            
            val finalEntry = _lineEntries.value[ingredientId]
            val success = finalEntry == null || (!finalEntry.isPending && finalEntry.error == null)
            completion.complete(success)
        }
    }

    private fun com.miara.cuentame.core.model.count.StockCountLine.toPersistedPreview() = StockCountLinePreview(
        countedQuantityBase = quantityBase,
        expectedQuantityBase = expectedQuantityBaseSnapshot,
        provisionalAdjustmentBase = adjustmentQuantityBase
            ?: quantityBase.subtract(expectedQuantityBaseSnapshot ?: BigDecimal.ZERO),
        willCreateOpeningBalance = expectedQuantityBaseSnapshot == null,
        averageCostBase = null,
        estimatedValueChange = null
    )
}
