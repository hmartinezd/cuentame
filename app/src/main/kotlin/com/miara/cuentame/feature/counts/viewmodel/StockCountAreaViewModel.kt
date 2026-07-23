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
import com.miara.cuentame.core.common.time.TimeProvider
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
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val quantityText: String = "",
    val lineId: String? = null,
    val preview: StockCountLinePreview? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: Throwable? = null
)

data class StockCountAreaUiState(
    val isLoading: Boolean = true,
    val isCompleting: Boolean = false,
    val searchQuery: String = "",
    val details: StockCountAreaDetails? = null,
    val lineEntries: List<StockCountLineEntry> = emptyList(),
    val searchResults: List<Ingredient> = emptyList(),
    val missingCount: Int = 0,
    val error: Throwable? = null
)

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

    private val countId = StockCountId(checkNotNull(savedStateHandle["countId"]))
    private val countAreaId = StockCountAreaId(checkNotNull(savedStateHandle["countAreaId"]))

    private val _searchQuery = MutableStateFlow("")
    private val _lineEntries = MutableStateFlow<Map<String, StockCountLineEntry>>(emptyMap())
    private val _isCompleting = MutableStateFlow(false)
    private val _error = MutableStateFlow<Throwable?>(null)

    private val saveJobs = mutableMapOf<String, Job>()

    private val searchResultsFlow = _searchQuery.flatMapLatest { query ->
        if (query.length < 2) kotlinx.coroutines.flow.flowOf(emptyList())
        else {
            repository.observeCountArea(countAreaId).filterNotNull().flatMapLatest { details ->
                ingredientRepository.observeIngredients(details.restaurantId, false)
                    .map { ingredients ->
                        ingredients.filter { it.name.contains(query, ignoreCase = true) }
                    }
            }
        }
    }

    val uiState: StateFlow<StockCountAreaUiState> = combine(
        combine(
            repository.observeCountArea(countAreaId),
            _lineEntries,
            _searchQuery
        ) { details, entriesMap, query ->
            Triple(details, entriesMap, query)
        },
        combine(
            _isCompleting,
            _error,
            searchResultsFlow
        ) { completing, error, searchResults ->
            Triple(completing, error, searchResults)
        }
    ) { (details, entriesMap, query), (completing, error, searchResults) ->
        val sortedEntries = entriesMap.values.sortedBy { it.ingredientName }
        StockCountAreaUiState(
            isLoading = details == null && error == null,
            isCompleting = completing,
            searchQuery = query,
            details = details,
            lineEntries = sortedEntries.filter { it.ingredientName.contains(query, ignoreCase = true) },
            searchResults = searchResults.filter { !entriesMap.containsKey(it.id.value) },
            missingCount = sortedEntries.count { it.quantityText.isBlank() },
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockCountAreaUiState()
    )

    init {
        viewModelScope.launch {
            repository.observeCountArea(countAreaId).collect { details ->
                if (details != null && _lineEntries.value.isEmpty()) {
                    initializeEntries(details)
                } else if (details != null) {
                    updateLineIds(details)
                }
            }
        }
    }

    private suspend fun initializeEntries(details: StockCountAreaDetails) {
        val missingIngredients = getMissingItemsUseCase(
            restaurantId = details.restaurantId,
            countId = countId,
            areaId = details.area.areaId,
            effectiveAt = details.effectiveAt
        )

        val entries = mutableMapOf<String, StockCountLineEntry>()

        details.lines.forEach { line ->
            val ingredient = ingredientRepository.getById(line.ingredientId) ?: return@forEach
            val options = ingredientRepository.getUnitOptions(line.ingredientId)
            val option = options.find { it.id == line.ingredientUnitOptionId } ?: return@forEach
            val category = ingredient.categoryId?.let { categoryRepository.getById(it) }

            entries[line.ingredientId.value] = StockCountLineEntry(
                ingredientId = line.ingredientId.value,
                ingredientName = ingredient.name,
                categoryName = category?.name,
                unitId = line.ingredientUnitOptionId.value,
                unitName = option.shortLabel,
                factorToBase = option.factorToBase,
                quantityText = line.quantityEntered.toPlainString(),
                lineId = line.id.value,
                isSaved = true
            )
        }

        missingIngredients.forEach { ingredient ->
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
                    quantityText = ""
                )
            }
        }

        _lineEntries.value = entries
        entries.values.forEach { updatePreview(it) }
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
                quantityText = ""
            )
            _lineEntries.update { it + (ingredient.id.value to entry) }
            updatePreview(entry)
            _searchQuery.value = ""
        }
    }

    fun onQuantityChanged(ingredientId: String, quantity: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        if (entry.quantityText == quantity) return

        _lineEntries.update { it + (ingredientId to entry.copy(
            quantityText = quantity,
            isSaved = false,
            error = null
        )) }

        val updatedEntry = _lineEntries.value[ingredientId]!!
        updatePreview(updatedEntry)

        saveJobs[ingredientId]?.cancel()
        if (quantity.isNotBlank() && quantity.toBigDecimalOrNull() != null) {
            saveJobs[ingredientId] = viewModelScope.launch {
                delay(500)
                saveLine(ingredientId)
            }
        }
    }

    private fun updatePreview(entry: StockCountLineEntry) {
        val qty = entry.quantityText.toBigDecimalOrNull() ?: return
        val details = uiState.value.details ?: return
        
        viewModelScope.launch {
            try {
                val preview = previewUseCase(
                    restaurantId = details.restaurantId,
                    ingredientId = IngredientId(entry.ingredientId),
                    areaId = details.area.areaId,
                    effectiveAt = details.effectiveAt,
                    quantityBase = qty.multiply(entry.factorToBase, MathContext.DECIMAL128)
                )
                _lineEntries.update { 
                    val current = it[entry.ingredientId]
                    if (current != null) {
                        it + (entry.ingredientId to current.copy(preview = preview))
                    } else it
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun saveLine(ingredientId: String) {
        val entry = _lineEntries.value[ingredientId] ?: return
        val qty = entry.quantityText.toBigDecimalOrNull() ?: return
        
        _lineEntries.update { it + (ingredientId to it[ingredientId]!!.copy(isSaving = true)) }

        try {
            val lineId = repository.saveLine(
                SaveStockCountLineCommand(
                    countId = countId,
                    countAreaId = countAreaId,
                    lineId = entry.lineId?.let { StockCountLineId(it) },
                    ingredientId = IngredientId(ingredientId),
                    ingredientUnitOptionId = IngredientUnitOptionId(entry.unitId),
                    quantityEntered = qty,
                    notes = null
                )
            )
            _lineEntries.update { it + (ingredientId to it[ingredientId]!!.copy(
                isSaving = false,
                isSaved = true,
                lineId = lineId.value
            )) }
        } catch (e: Exception) {
            _lineEntries.update { it + (ingredientId to it[ingredientId]!!.copy(
                isSaving = false,
                error = e
            )) }
        }
    }

    fun onCompleteArea() {
        if (_isCompleting.value) return
        
        if (_lineEntries.value.values.any { it.isSaving }) {
            _error.value = ValidationError.PendingCountSaves
            return
        }

        _isCompleting.value = true
        viewModelScope.launch {
            try {
                repository.completeArea(countId, countAreaId)
            } catch (e: Exception) {
                _isCompleting.value = false
                _error.value = e
            }
        }
    }

    fun onReopenArea() {
        viewModelScope.launch {
            try {
                repository.reopenArea(countId, countAreaId)
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
