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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class StockCountLineEntry(
    val ingredientId: String,
    val ingredientName: String,
    val categoryName: String?,
    val unitId: String,
    val unitName: String,
    val factorToBase: BigDecimal,
    val baseUnitName: String,
    val quantityText: String = "",
    val lineId: String? = null,
    val preview: StockCountLinePreview? = null,
    val hasUserEdit: Boolean = false,
    val editRevision: Long = 0,
    val savedRevision: Long = -1,
    val isSaving: Boolean = false,
    val error: Throwable? = null,
    val unitOptions: List<IngredientUnitOption> = emptyList()
) {
    val isSaved: Boolean = hasUserEdit && editRevision == savedRevision && error == null && !isSaving
    val isPending: Boolean = hasUserEdit && (editRevision > savedRevision || isSaving)
}

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
    val details: StockCountAreaDetails? = null,
    val lineEntries: List<StockCountLineEntry> = emptyList(),
    val searchResults: List<Ingredient> = emptyList(),
    val archivedWarnings: List<ArchivedCountCandidate> = emptyList(),
    val missingCount: Int = 0,
    val error: Throwable? = null,
    val canEdit: Boolean = false,
    val canReopen: Boolean = false
)

sealed interface StockCountAreaEvent {
    data object AreaCompleted : StockCountAreaEvent
    data object NavigateBack : StockCountAreaEvent
    data class ShowError(val error: Throwable) : StockCountAreaEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockCountAreaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockCountRepository,
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
    private val _lineEntries = MutableStateFlow<Map<String, StockCountLineEntry>>(emptyMap())
    private val _archivedWarnings = MutableStateFlow<List<ArchivedCountCandidate>>(emptyList())
    private val _missingActiveCandidates = MutableStateFlow<List<Ingredient>>(emptyList())
    private val _isCompleting = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _events = Channel<StockCountAreaEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val saveJobs = mutableMapOf<String, Job>()
    private val previewJobs = mutableMapOf<String, Job>()
    private val saveMutexes = mutableMapOf<String, Mutex>()

    private data class CombinedOtherStates(
        val completing: Boolean,
        val error: Throwable?,
        val searchResults: List<Ingredient>,
        val archivedWarnings: List<ArchivedCountCandidate>,
        val missingCandidates: List<Ingredient>,
        val hasLoadedOnce: Boolean
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
        _lineEntries,
        _searchQuery,
        combine(
            _isCompleting,
            _error,
            searchResultsFlow,
            _archivedWarnings,
            _missingActiveCandidates,
            _hasLoadedOnce
        ) { args ->
            CombinedOtherStates(
                completing = args[0] as Boolean,
                error = args[1] as Throwable?,
                searchResults = args[2] as List<Ingredient>,
                archivedWarnings = args[3] as List<ArchivedCountCandidate>,
                missingCandidates = args[4] as List<Ingredient>,
                hasLoadedOnce = args[5] as Boolean
            )
        }
    ) { details, entriesMap, query, others ->
        val screenState = when {
            countId == null || countAreaId == null -> 
                StockCountAreaScreenState.InvalidRoute
            !others.hasLoadedOnce && others.error == null -> StockCountAreaScreenState.Loading
            others.error != null && details == null -> StockCountAreaScreenState.Error(others.error)
            details == null -> StockCountAreaScreenState.NotFound
            details.area.stockCountId != countId -> StockCountAreaScreenState.OwnershipMismatch
            else -> StockCountAreaScreenState.Ready
        }

        val sortedEntries = entriesMap.values.sortedBy { it.ingredientName }
        val countStatus = details?.countStatus ?: StockCountStatus.DRAFT
        val areaStatus = details?.area?.status ?: CountAreaStatus.NOT_STARTED

        StockCountAreaUiState(
            screenState = screenState,
            isCompleting = others.completing,
            hasPendingSaves = entriesMap.values.any { it.isPending },
            searchQuery = query,
            details = details,
            lineEntries = sortedEntries.filter { it.ingredientName.contains(query, ignoreCase = true) },
            searchResults = others.searchResults.filter { !entriesMap.containsKey(it.id.value) },
            archivedWarnings = others.archivedWarnings,
            missingCount = others.archivedWarnings.size + others.missingCandidates.size,
            error = others.error,
            canEdit = countStatus == StockCountStatus.DRAFT && areaStatus != CountAreaStatus.COMPLETED,
            canReopen = countStatus == StockCountStatus.DRAFT && areaStatus == CountAreaStatus.COMPLETED
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
                baseUnitName = baseUnit?.shortLabel ?: "units",
                quantityText = line.quantityEntered.toPlainString(),
                lineId = line.id.value,
                hasUserEdit = true,
                editRevision = 0,
                savedRevision = 0,
                unitOptions = if (isEditable) options.filter { it.isActive || it.id == line.ingredientUnitOptionId } else options,
                preview = if (!isEditable) StockCountLinePreview(
                    countedQuantityBase = line.quantityBase,
                    expectedQuantityBase = line.expectedQuantityBaseSnapshot,
                    provisionalAdjustmentBase = line.adjustmentQuantityBase ?: BigDecimal.ZERO,
                    willCreateOpeningBalance = line.expectedQuantityBaseSnapshot == null,
                    averageCostBase = null,
                    estimatedValueChange = null
                ) else null
            )
        }

        if (isEditable) {
            candidateResult.missingActiveCandidates.forEach { ingredient ->
                if (!entries.containsKey(ingredient.id.value)) {
                    val options = ingredientRepository.getUnitOptions(ingredient.id)
                    val option = options.find { it.isDefaultCount } ?: options.find { it.isBase } ?: return@forEach
                    val category = ingredient.categoryId?.let { categoryRepository.getById(it) }

                    entries[ingredient.id.value] = StockCountLineEntry(
                        ingredientId = ingredient.id.value,
                        ingredientName = ingredient.name,
                        categoryName = category?.name,
                        unitId = option.id.value,
                        unitName = option.shortLabel,
                        factorToBase = option.factorToBase,
                        baseUnitName = options.find { it.isBase }?.shortLabel ?: "units",
                        quantityText = "",
                        hasUserEdit = false,
                        unitOptions = options.filter { it.isActive }
                    )
                }
            }
        }

        _lineEntries.value = entries
        _archivedWarnings.value = candidateResult.archivedBalanceWarnings
        _missingActiveCandidates.value = candidateResult.missingActiveCandidates
        if (isEditable) {
            entries.values.forEach { updatePreview(it) }
        }
    }

    private fun updateLineIds(details: StockCountAreaDetails) {
        _lineEntries.update { entries ->
            entries.mapValues { (id, entry) ->
                val line = details.lines.find { it.ingredientId.value == id }
                if (line != null) entry.copy(lineId = line.id.value) else entry
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onAddIngredient(ingredient: Ingredient) {
        if (_lineEntries.value.containsKey(ingredient.id.value)) return

        viewModelScope.launch {
            val options = ingredientRepository.getUnitOptions(ingredient.id)
            val option = options.find { it.isDefaultCount } ?: options.find { it.isBase } ?: return@launch
            val category = ingredient.categoryId?.let { categoryRepository.getById(it) }

            val entry = StockCountLineEntry(
                ingredientId = ingredient.id.value,
                ingredientName = ingredient.name,
                categoryName = category?.name,
                unitId = option.id.value,
                unitName = option.shortLabel,
                factorToBase = option.factorToBase,
                baseUnitName = options.find { it.isBase }?.shortLabel ?: "units",
                quantityText = "",
                hasUserEdit = false,
                unitOptions = options.filter { it.isActive }
            )
            _lineEntries.update { it + (ingredient.id.value to entry) }
            updatePreview(entry)
            _searchQuery.value = ""
        }
    }

    fun onQuantityChanged(ingredientId: String, quantity: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        if (entry.quantityText == quantity) return

        val newRevision = entry.editRevision + 1
        val updatedEntry = entry.copy(
            quantityText = quantity,
            editRevision = newRevision,
            hasUserEdit = true,
            error = null
        )
        _lineEntries.update { it + (ingredientId to updatedEntry) }

        updatePreview(updatedEntry)

        saveJobs[ingredientId]?.cancel()
        val parsed = DecimalParser.parse(quantity)
        if (quantity.isNotBlank() && parsed != null && parsed >= BigDecimal.ZERO) {
            saveJobs[ingredientId] = viewModelScope.launch {
                delay(500)
                saveLine(ingredientId, newRevision)
            }
        } else if (quantity.isNotBlank()) {
             val error = if (parsed != null && parsed < BigDecimal.ZERO) ValidationError.InvalidCountQuantity else ValidationError.InvalidDecimal
             _lineEntries.update { it + (ingredientId to updatedEntry.copy(error = error)) }
        }
    }

    fun onUnitChanged(ingredientId: String, optionId: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        val option = entry.unitOptions.find { it.id.value == optionId } ?: return
        if (entry.unitId == optionId) return

        val newRevision = entry.editRevision + 1
        val updatedEntry = entry.copy(
            unitId = optionId,
            unitName = option.shortLabel,
            factorToBase = option.factorToBase,
            editRevision = newRevision,
            hasUserEdit = true,
            error = null
        )
        _lineEntries.update { it + (ingredientId to updatedEntry) }
        updatePreview(updatedEntry)
        
        saveJobs[ingredientId]?.cancel()
        val parsed = DecimalParser.parse(updatedEntry.quantityText)
        if (updatedEntry.quantityText.isNotBlank() && parsed != null && parsed >= BigDecimal.ZERO) {
            saveJobs[ingredientId] = viewModelScope.launch {
                saveLine(ingredientId, newRevision)
            }
        }
    }

    fun onDeleteLine(ingredientId: String) {
        val cid = countId ?: return
        val aid = countAreaId ?: return
        val entry = _lineEntries.value[ingredientId] ?: return
        
        saveJobs[ingredientId]?.cancel()
        previewJobs[ingredientId]?.cancel()

        val lineId = entry.lineId ?: run {
            _lineEntries.update { it - ingredientId }
            updateMissingCountAfterDelete()
            return
        }

        viewModelScope.launch {
            try {
                repository.deleteLine(cid, aid, StockCountLineId(lineId))
                _lineEntries.update { it - ingredientId }
                updateMissingCountAfterDelete()
            } catch (e: Exception) {
                _events.send(StockCountAreaEvent.ShowError(e))
            }
        }
    }

    private fun updateMissingCountAfterDelete() {
        viewModelScope.launch {
            val details = uiState.value.details ?: return@launch
            val result = getMissingItemsUseCase(details.restaurantId, countId!!, details.area.areaId, details.effectiveAt)
            _missingActiveCandidates.value = result.missingActiveCandidates
        }
    }

    private fun updatePreview(entry: StockCountLineEntry) {
        previewJobs[entry.ingredientId]?.cancel()
        val parsed = DecimalParser.parse(entry.quantityText)
        if (parsed == null || parsed < BigDecimal.ZERO) {
            _lineEntries.update { entries ->
                val current = entries[entry.ingredientId]
                if (current != null && current.editRevision == entry.editRevision) {
                    entries + (entry.ingredientId to current.copy(preview = null))
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
                    effectiveAt = details.effectiveAt,
                    quantityBase = parsed.multiply(entry.factorToBase, MathContext.DECIMAL128)
                )
                _lineEntries.update { entries ->
                    val current = entries[entry.ingredientId]
                    if (current != null && current.editRevision == revision) {
                        entries + (entry.ingredientId to current.copy(preview = preview))
                    } else entries
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun saveLine(ingredientId: String, revision: Long) {
        val cid = countId ?: return
        val aid = countAreaId ?: return
        val mutex = saveMutexes.getOrPut(ingredientId) { Mutex() }
        mutex.withLock {
            val entry = _lineEntries.value[ingredientId] ?: return
            if (entry.editRevision != revision || (entry.savedRevision >= revision && entry.lineId != null)) return
            
            val parsed = DecimalParser.parse(entry.quantityText) ?: return
            if (parsed < BigDecimal.ZERO) return

            _lineEntries.update { it + (ingredientId to it[ingredientId]!!.copy(isSaving = true)) }

            try {
                val lineId = repository.saveLine(
                    SaveStockCountLineCommand(
                        countId = cid,
                        countAreaId = aid,
                        lineId = entry.lineId?.let { StockCountLineId(it) },
                        ingredientId = IngredientId(ingredientId),
                        ingredientUnitOptionId = IngredientUnitOptionId(entry.unitId),
                        quantityEntered = parsed,
                        notes = null
                    )
                )
                _lineEntries.update { entries ->
                    val current = entries[ingredientId]
                    val updated = if (current != null && current.editRevision == revision) {
                        current.copy(
                            isSaving = false,
                            savedRevision = revision,
                            lineId = lineId.value,
                            error = null
                        )
                    } else if (current != null) {
                        current.copy(isSaving = false, lineId = lineId.value)
                    } else null
                    
                    if (updated != null) entries + (ingredientId to updated) else entries
                }
                updateMissingCountAfterDelete()
            } catch (e: Exception) {
                _lineEntries.update { entries ->
                    val current = entries[ingredientId]
                    val updated = if (current != null && current.editRevision == revision) {
                        current.copy(isSaving = false, error = e)
                    } else if (current != null) {
                        current.copy(isSaving = false)
                    } else null
                    
                    if (updated != null) entries + (ingredientId to updated) else entries
                }
            }
        }
    }

    suspend fun flushPendingSaves(): Boolean {
        val pending = _lineEntries.value.values.filter { it.isPending }
        if (pending.isEmpty()) return true

        pending.forEach { saveJobs[it.ingredientId]?.cancel() }

        if (pending.any { 
            val parsed = DecimalParser.parse(it.quantityText)
            parsed == null || parsed < BigDecimal.ZERO 
        }) return false

        val jobs = pending.map { entry ->
            viewModelScope.launch {
                saveLine(entry.ingredientId, entry.editRevision)
            }
        }
        jobs.joinAll()
        
        return _lineEntries.value.values.none { it.isPending || it.error != null }
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
}
