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
import com.miara.cuentame.feature.activity.logic.InventoryActivityDateUtils.toInterval
import com.miara.cuentame.feature.activity.logic.InventoryActivityTextResolver
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.presentation.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
        val localeTag: String,
        val activeFilterCount: Int,
        val today: LocalDate
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
    val textResolver: InventoryActivityTextResolver,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _filters = MutableStateFlow(restoreFilters())
    val filters: StateFlow<InventoryActivityFilters> = _filters.asStateFlow()

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("searchQuery") ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private fun restoreFilters(): InventoryActivityFilters {
        val kind = savedStateHandle.get<String>("dateRangeKind")
        val customStart = savedStateHandle.get<String>("customStartDate")?.let { 
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val customEnd = savedStateHandle.get<String>("customEndDate")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        
        val dateRange = when (kind) {
            "Last7Days" -> InventoryActivityDateRange.Last7Days
            "Last30Days" -> InventoryActivityDateRange.Last30Days
            "Last90Days" -> InventoryActivityDateRange.Last90Days
            "Custom" -> if (customStart != null && customEnd != null && !customStart.isAfter(customEnd)) {
                InventoryActivityDateRange.Custom(customStart, customEnd)
            } else InventoryActivityDateRange.Last30Days
            else -> InventoryActivityDateRange.Last30Days
        }

        val initialIngredientId = savedStateHandle.get<String>("ingredientId")
            ?.takeIf { it.isNotBlank() && it != "{ingredientId}" }
            ?.let { IngredientId(it) }
            
        val initialAreaId = savedStateHandle.get<String>("areaId")
            ?.takeIf { it.isNotBlank() && it != "{areaId}" }
            ?.let { InventoryAreaId(it) }

        val categoryNames = savedStateHandle.get<List<String>>("categories")
        val categories = categoryNames?.mapNotNull { name ->
            runCatching { InventoryActivityCategory.valueOf(name) }.getOrNull()
        }?.toSet()?.takeIf { it.isNotEmpty() } ?: InventoryActivityCategory.entries.toSet()

        val directionName = savedStateHandle.get<String>("direction")
        val direction = directionName?.let { name ->
            runCatching { InventoryActivityDirection.valueOf(name) }.getOrNull()
        } ?: InventoryActivityDirection.ALL

        val includeReversals = savedStateHandle.get<Boolean>("includeReversals") ?: true

        return InventoryActivityFilters(
            dateRange = dateRange,
            ingredientId = initialIngredientId,
            areaId = initialAreaId,
            categories = categories,
            direction = direction,
            includeReversals = includeReversals
        )
    }

    private fun persistFilters(filters: InventoryActivityFilters) {
        val kind = when (filters.dateRange) {
            InventoryActivityDateRange.Last7Days -> "Last7Days"
            InventoryActivityDateRange.Last30Days -> "Last30Days"
            InventoryActivityDateRange.Last90Days -> "Last90Days"
            is InventoryActivityDateRange.Custom -> "Custom"
        }
        savedStateHandle["dateRangeKind"] = kind
        if (filters.dateRange is InventoryActivityDateRange.Custom) {
            savedStateHandle["customStartDate"] = filters.dateRange.startDate.toString()
            savedStateHandle["customEndDate"] = filters.dateRange.endDateInclusive.toString()
        }

        savedStateHandle["ingredientId"] = filters.ingredientId?.value
        savedStateHandle["areaId"] = filters.areaId?.value
        savedStateHandle["categories"] = filters.categories.map { it.name }.toList()
        savedStateHandle["direction"] = filters.direction.name
        savedStateHandle["includeReversals"] = filters.includeReversals
    }

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
            val today = LocalDate.now(zoneId)
            val interval = filters.dateRange.toInterval(today, zoneId)
            val query = InventoryActivityQuery(
                restaurantId = restaurant.id,
                startInclusive = interval.startInclusive,
                endExclusive = interval.endExclusive,
                ingredientId = filters.ingredientId,
                areaId = filters.areaId
            )
            
            val locale = Locale.forLanguageTag(restaurant.localeTag)

            combine(
                activityRepository.observeActivity(query),
                ingredientRepository.observeIngredients(restaurant.id, includeArchived = true),
                areaRepository.observeAllAreas()
            ) { items, ingredients, areas ->
                val filteredItems = items.filter { item ->
                    val matchesCategory = filters.categories.contains(item.movement.movementType.toInventoryActivityCategory())
                    val matchesDirection = when (filters.direction) {
                        InventoryActivityDirection.ALL -> true
                        InventoryActivityDirection.IN -> item.movement.quantityBaseSigned > BigDecimal.ZERO
                        InventoryActivityDirection.OUT -> item.movement.quantityBaseSigned < BigDecimal.ZERO
                    }
                    val matchesReversal = filters.includeReversals || item.movement.movementType != InventoryMovementType.REVERSAL
                    val matchesSearch = search.isBlank() || item.matches(search, textResolver, locale)
                    
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
                        localeTag = restaurant.localeTag,
                        activeFilterCount = filters.countActive(),
                        today = today
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
        persistFilters(newFilters)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        savedStateHandle["searchQuery"] = query
    }

    fun resetFilters() {
        val defaultFilters = InventoryActivityFilters()
        _filters.value = defaultFilters
        _searchQuery.value = ""
        persistFilters(defaultFilters)
        savedStateHandle["searchQuery"] = ""
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
        var valuePresentCount = 0
        
        val mc = MathContext.DECIMAL128
        
        items.forEach { item ->
            val value = item.movement.totalValueSnapshot
            if (value != null) {
                valuePresentCount++
                if (value > BigDecimal.ZERO) valueAdded = valueAdded.add(value, mc)
                else if (value < BigDecimal.ZERO) valueRemoved = valueRemoved.add(value.abs(), mc)
            }
        }

        val coverage = when {
            items.isEmpty() -> InventoryActivityValueCoverage.NONE
            valuePresentCount == items.size -> InventoryActivityValueCoverage.COMPLETE
            valuePresentCount > 0 -> InventoryActivityValueCoverage.PARTIAL
            else -> InventoryActivityValueCoverage.UNAVAILABLE
        }

        val distinctIngredientUnits = items.map { it.movement.ingredientId to it.baseUnitSymbol }.distinct()
        val quantitySummary = if (distinctIngredientUnits.size == 1) {
            val first = items.first()
            var qIn = BigDecimal.ZERO
            var qOut = BigDecimal.ZERO
            items.forEach { 
                if (it.movement.quantityBaseSigned > BigDecimal.ZERO) qIn = qIn.add(it.movement.quantityBaseSigned, mc)
                else if (it.movement.quantityBaseSigned < BigDecimal.ZERO) qOut = qOut.add(it.movement.quantityBaseSigned.abs(), mc)
            }
            InventoryActivityQuantitySummary(
                ingredientName = first.ingredientName,
                baseUnitSymbol = first.baseUnitSymbol,
                quantityIn = qIn,
                quantityOut = qOut,
                netQuantity = qIn.subtract(qOut, mc)
            )
        } else null

        return InventoryActivitySummary(
            movementCount = items.size,
            incomingMovementCount = incoming,
            outgoingMovementCount = outgoing,
            reversalCount = reversals,
            valueAdded = valueAdded,
            valueRemoved = valueRemoved,
            valueCoverage = coverage,
            quantitySummary = quantitySummary
        )
    }

    private fun InventoryActivityItem.matches(query: String, resolver: InventoryActivityTextResolver, locale: Locale): Boolean {
        val normalized = query.trim().lowercase(locale)
        val catText = resolver.categoryText(movement.movementType.toInventoryActivityCategory()).lowercase(locale)
        val title = resolver.sourceTitle(sourceInfo).lowercase(locale)
        val subtitle = resolver.sourceSubtitle(sourceInfo)?.lowercase(locale)
        
        return ingredientName.lowercase(locale).contains(normalized) ||
                areaName.lowercase(locale).contains(normalized) ||
                catText.contains(normalized) ||
                title.contains(normalized) ||
                subtitle?.contains(normalized) == true ||
                matchSourceDetails(normalized, locale, resolver)
    }

    private fun InventoryActivityItem.matchSourceDetails(query: String, locale: Locale, resolver: InventoryActivityTextResolver): Boolean {
        return when (val info = sourceInfo) {
            is InventoryActivitySourceInfo.Purchase -> {
                info.supplierName?.lowercase(locale)?.contains(query) == true ||
                info.invoiceNumber?.lowercase(locale)?.contains(query) == true
            }
            is InventoryActivitySourceInfo.Waste -> {
                info.reason?.let { resolver.wasteReasonText(it).lowercase(locale) }?.contains(query) == true ||
                info.sourceAreaName?.lowercase(locale)?.contains(query) == true
            }
            is InventoryActivitySourceInfo.StockCount -> {
                info.countName?.lowercase(locale)?.contains(query) == true
            }
            is InventoryActivitySourceInfo.Production -> {
                info.recipeName?.lowercase(locale)?.contains(query) == true ||
                info.status?.let { resolver.productionStatusText(it).lowercase(locale) }?.contains(query) == true
            }
            is InventoryActivitySourceInfo.Other -> false
        }
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
}
