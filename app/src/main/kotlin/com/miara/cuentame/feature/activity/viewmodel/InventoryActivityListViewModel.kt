package com.miara.cuentame.feature.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface InventoryActivityListScreenState {
    data object Loading : InventoryActivityListScreenState
    data object SetupRequired : InventoryActivityListScreenState
    data object Empty : InventoryActivityListScreenState
    data class Ready(
        val items: List<InventoryActivityItem>,
        val summary: InventoryActivitySummary,
        val filters: InventoryActivityFilters,
        val availableIngredients: List<IngredientFilterOption>,
        val availableAreas: List<AreaFilterOption>,
        val currencyCode: String,
        val activeFilterCount: Int
    ) : InventoryActivityListScreenState
    data class LoadError(val message: UiMessage) : InventoryActivityListScreenState
}

data class IngredientFilterOption(val id: IngredientId, val name: String)
data class AreaFilterOption(val id: InventoryAreaId, val name: String)

@HiltViewModel
class InventoryActivityListViewModel @Inject constructor(
    private val activityRepository: InventoryActivityRepository,
    private val ingredientRepository: IngredientRepository,
    private val areaRepository: InventoryAreaRepository,
    private val restaurantRepository: RestaurantRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialIngredientId = savedStateHandle.get<String>("ingredientId")?.let { IngredientId(it) }
    private val initialAreaId = savedStateHandle.get<String>("areaId")?.let { InventoryAreaId(it) }

    private val _filters = MutableStateFlow(
        InventoryActivityFilters(
            ingredientId = initialIngredientId,
            areaId = initialAreaId
        )
    )
    val filters: StateFlow<InventoryActivityFilters> = _filters.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<InventoryActivityListScreenState> = combine(
        restaurantRepository.observeRestaurant(),
        _filters,
        _searchQuery.debounce(300L),
        retryTrigger
    ) { restaurant, filters, search, _ ->
        restaurant to (filters to search)
    }.flatMapLatest { (restaurant, filtersAndSearch) ->
        val (filters, search) = filtersAndSearch
        if (restaurant == null) {
            flowOf(InventoryActivityListScreenState.SetupRequired)
        } else {
            val zoneId = ZoneId.systemDefault()
            val (start, end) = filters.dateRange.toInterval(zoneId)
            val query = InventoryActivityQuery(
                restaurantId = restaurant.id,
                startInclusive = start,
                endExclusive = end,
                ingredientId = filters.ingredientId,
                areaId = filters.areaId
            )
            
            combine(
                activityRepository.observeActivity(query),
                ingredientRepository.observeIngredients(restaurant.id, includeArchived = true),
                areaRepository.observeAllAreas()
            ) { items, ingredients, areas ->
                val filteredItems = items.filter { item ->
                    val matchesCategory = filters.categories.contains(item.movement.movementType.toActivityCategory())
                    val matchesDirection = when (filters.direction) {
                        InventoryActivityDirection.ALL -> true
                        InventoryActivityDirection.IN -> item.movement.quantityBaseSigned > BigDecimal.ZERO
                        InventoryActivityDirection.OUT -> item.movement.quantityBaseSigned < BigDecimal.ZERO
                    }
                    val matchesReversal = filters.includeReversals || item.movement.movementType != InventoryMovementType.REVERSAL
                    val matchesSearch = search.isBlank() || item.matches(search)
                    
                    matchesCategory && matchesDirection && matchesReversal && matchesSearch
                }

                if (items.isEmpty() && filters.isDefault()) {
                    InventoryActivityListScreenState.Empty
                } else {
                    InventoryActivityListScreenState.Ready(
                        items = filteredItems,
                        summary = calculateSummary(filteredItems),
                        filters = filters,
                        availableIngredients = ingredients.map { IngredientFilterOption(it.id, it.name) },
                        availableAreas = areas.map { AreaFilterOption(it.id, it.name) },
                        currencyCode = restaurant.currencyCode,
                        activeFilterCount = filters.countActive()
                    )
                }
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(InventoryActivityListScreenState.LoadError(UiMessage.Resource(R.string.error_generic)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryActivityListScreenState.Loading)

    fun onFilterChange(newFilters: InventoryActivityFilters) {
        _filters.value = newFilters
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onRetry() {
        retryTrigger.value += 1
    }

    private fun calculateSummary(items: List<InventoryActivityItem>): InventoryActivitySummary {
        val incoming = items.count { it.movement.quantityBaseSigned > BigDecimal.ZERO }
        val outgoing = items.count { it.movement.quantityBaseSigned < BigDecimal.ZERO }
        val reversals = items.count { it.movement.movementType == InventoryMovementType.REVERSAL }
        
        var valueAdded = BigDecimal.ZERO
        var valueRemoved = BigDecimal.ZERO
        var anyValueUnavailable = false
        
        items.forEach { item ->
            val value = item.movement.totalValueSnapshot
            if (value == null) {
                anyValueUnavailable = true
            } else {
                if (value > BigDecimal.ZERO) valueAdded += value
                else if (value < BigDecimal.ZERO) valueRemoved += value.abs()
            }
        }

        val distinctIngredients = items.map { it.movement.ingredientId }.distinct()
        val quantitySummary = if (distinctIngredients.size == 1) {
            val ingredient = items.first()
            var qIn = BigDecimal.ZERO
            var qOut = BigDecimal.ZERO
            items.forEach { 
                if (it.movement.quantityBaseSigned > BigDecimal.ZERO) qIn += it.movement.quantityBaseSigned
                else if (it.movement.quantityBaseSigned < BigDecimal.ZERO) qOut += it.movement.quantityBaseSigned.abs()
            }
            InventoryActivityQuantitySummary(
                ingredientName = ingredient.ingredientName,
                baseUnitSymbol = ingredient.baseUnitSymbol,
                quantityIn = qIn,
                quantityOut = qOut,
                netQuantity = qIn - qOut
            )
        } else null

        return InventoryActivitySummary(
            movementCount = items.size,
            incomingMovementCount = incoming,
            outgoingMovementCount = outgoing,
            reversalCount = reversals,
            valueAdded = if (anyValueUnavailable && valueAdded == BigDecimal.ZERO) null else valueAdded,
            valueRemoved = if (anyValueUnavailable && valueRemoved == BigDecimal.ZERO) null else valueRemoved,
            quantitySummary = quantitySummary
        )
    }

    private fun InventoryActivityItem.matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        return ingredientName.lowercase().contains(normalized) ||
                areaName.lowercase().contains(normalized) ||
                sourceDisplay.title.lowercase().contains(normalized) ||
                sourceDisplay.subtitle?.lowercase()?.contains(normalized) == true ||
                movement.movementType.toActivityCategory().name.lowercase().contains(normalized)
    }

    private fun InventoryActivityFilters.isDefault(): Boolean {
        return dateRange == InventoryActivityDateRange.Last30Days &&
                ingredientId == null &&
                areaId == null &&
                categories == InventoryActivityCategory.entries.toSet() &&
                direction == InventoryActivityDirection.ALL &&
                includeReversals
    }

    private fun InventoryActivityFilters.countActive(): Int {
        var count = 0
        if (dateRange != InventoryActivityDateRange.Last30Days) count++
        if (ingredientId != null) count++
        if (areaId != null) count++
        if (categories.size != InventoryActivityCategory.entries.size) count++
        if (direction != InventoryActivityDirection.ALL) count++
        if (!includeReversals) count++
        return count
    }

    private fun InventoryActivityDateRange.toInterval(zoneId: ZoneId): Pair<Instant, Instant> {
        val now = LocalDate.now(zoneId)
        val (startDate, endDateInclusive) = when (this) {
            InventoryActivityDateRange.Last7Days -> now.minusDays(6) to now
            InventoryActivityDateRange.Last30Days -> now.minusDays(29) to now
            InventoryActivityDateRange.Last90Days -> now.minusDays(89) to now
            is InventoryActivityDateRange.Custom -> startDate to endDateInclusive
        }
        val startInclusive = startDate.atStartOfDay(zoneId).toInstant()
        val endExclusive = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        return startInclusive to endExclusive
    }

    private fun InventoryMovementType.toActivityCategory(): InventoryActivityCategory = when (this) {
        InventoryMovementType.PURCHASE -> InventoryActivityCategory.PURCHASE
        InventoryMovementType.WASTE -> InventoryActivityCategory.WASTE
        InventoryMovementType.COUNT_ADJUSTMENT -> InventoryActivityCategory.STOCK_COUNT
        InventoryMovementType.MANUAL_ADJUSTMENT -> InventoryActivityCategory.OTHER
        InventoryMovementType.OPENING_BALANCE -> InventoryActivityCategory.OTHER
        InventoryMovementType.REVERSAL -> InventoryActivityCategory.REVERSAL
        InventoryMovementType.PRODUCTION_CONSUMPTION -> InventoryActivityCategory.PRODUCTION_CONSUMPTION
        InventoryMovementType.PRODUCTION_OUTPUT -> InventoryActivityCategory.PRODUCTION_OUTPUT
    }
}
