package com.miara.cuentame.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data object SetupRequired : HomeScreenState
    data class Ready(
        val restaurantId: RestaurantId,
        val restaurantName: String,
        val currencyCode: String,
        val localeTag: String,
        val selectedRange: DashboardDateRange,
        val loadedRange: DashboardDateRange,
        val dashboard: DashboardUiModel,
        val setup: SetupReadinessUiModel = SetupReadinessUiModel(emptyList(), 0, 0, emptyList(), false),
        val isRefreshing: Boolean = false,
        val refreshError: Boolean = false,
        val menuManagementEnabled: Boolean = true
    ) : HomeScreenState
    data class Error(
        val selectedRange: DashboardDateRange,
        val cause: Throwable
    ) : HomeScreenState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val dashboardRepository: DashboardRepository,
    private val ingredientRepository: IngredientRepository = EmptyIngredientRepository,
    private val inventoryAreaRepository: InventoryAreaRepository = EmptyAreaRepository,
    private val stockCountRepository: StockCountRepository = EmptyStockCountRepository,
    private val preferencesRepository: AppPreferencesRepository? = null
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DashboardDateRange.LAST_30_DAYS)
    val selectedRange: StateFlow<DashboardDateRange> = _selectedRange.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeScreenState> = combine(
        restaurantRepository.observeRestaurant(),
        _selectedRange,
        _retryTrigger
    ) { restaurant, range, _ ->
        restaurant to range
    }.flatMapLatest { (restaurant, range) ->
        if (restaurant == null) {
            flowOf(HomeScreenState.SetupRequired)
        } else {
            combine(
                dashboardRepository.observeDashboard(restaurant.id, range),
                ingredientRepository.observeIngredients(restaurant.id, false),
                inventoryAreaRepository.observeActiveAreas(),
                stockCountRepository.observeHasCompletedCount(restaurant.id),
                preferencesRepository?.observePreferences() ?: flowOf(com.miara.cuentame.core.preferences.model.AppPreferences.DEFAULT)
            ) { snapshot, ingredients, areas, hasCompletedCount, preferences ->
                    val activeAreaIds = areas.map { it.id }.toSet()
                    HomeScreenState.Ready(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        currencyCode = restaurant.currencyCode,
                        localeTag = restaurant.localeTag,
                        selectedRange = range,
                        loadedRange = range,
                        dashboard = mapToUiModel(snapshot),
                        setup = SetupReadinessUiModel(
                            areas = areas,
                            ingredientCount = ingredients.size,
                            invalidUnitCount = snapshot.activeIngredientsMissingOptionsCount,
                            unassignedIngredientIds = ingredients
                                .filter { it.defaultAreaId == null || it.defaultAreaId !in activeAreaIds }
                                .map { it.id },
                            hasCompletedInitialCount = hasCompletedCount
                        ),
                        menuManagementEnabled = preferences.menuManagementEnabled
                    ) as HomeScreenState
                }
                .onStart {
                    val current = uiState.value
                    if (current is HomeScreenState.Ready && current.restaurantId == restaurant.id) {
                        emit(current.copy(selectedRange = range, isRefreshing = true, refreshError = false))
                    } else {
                        emit(HomeScreenState.Loading)
                    }
                }
                .catch { cause ->
                    val current = uiState.value
                    if (current is HomeScreenState.Ready) {
                        emit(current.copy(selectedRange = range, isRefreshing = false, refreshError = true))
                    } else {
                        emit(HomeScreenState.Error(range, cause))
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeScreenState.Loading
    )

    fun onRangeSelected(range: DashboardDateRange) {
        _selectedRange.value = range
    }

    fun onRetry() {
        _retryTrigger.value++
    }

    fun assignAllUnassignedIngredients(areaId: InventoryAreaId) {
        val state = uiState.value as? HomeScreenState.Ready ?: return
        viewModelScope.launch {
            ingredientRepository.assignDefaultArea(state.setup.unassignedIngredientIds, areaId)
        }
    }

    private fun mapToUiModel(snapshot: DashboardSnapshot): DashboardUiModel {
        val stockedCount = snapshot.inventory.stockedIngredientCount
        val valuedCount = snapshot.inventory.valuedIngredientCount
        // Use BigDecimal for coverage calculation to avoid Double precision issues
        val coverage = if (stockedCount > 0) {
            BigDecimal(valuedCount).divide(BigDecimal(stockedCount), 3, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(1, java.math.RoundingMode.HALF_UP)
        } else null

        return DashboardUiModel(
            inventoryValue = snapshot.inventory.totalValue,
            valuedIngredientCount = valuedCount,
            stockedIngredientCount = stockedCount,
            costCoverage = coverage,
            missingCostCount = snapshot.inventory.missingCostCount,
            missingOptionsCount = snapshot.activeIngredientsMissingOptionsCount,
            purchaseSpend = mapComparison(snapshot.purchases),
            wasteValue = mapComparison(snapshot.waste),
            negativeBalanceCount = snapshot.negativeBalanceCount,
            completedCountCount = snapshot.completedCountCount,
            mostRecentCompletedCountAt = snapshot.mostRecentCompletedCountAt,
            adjustedLineCount = snapshot.adjustedLineCount,
            topWasteItems = snapshot.topWasteItems,
            recentActivity = snapshot.recentActivity
        )
    }

    private fun mapComparison(comparison: MetricComparison): DashboardMetricUiModel {
        val state = when {
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) > 0 -> 
                MetricComparisonState.NEW
            comparison.previous.compareTo(BigDecimal.ZERO) == 0 && comparison.current.compareTo(BigDecimal.ZERO) == 0 -> 
                MetricComparisonState.NO_CHANGE
            comparison.percentageChange == null -> MetricComparisonState.UNAVAILABLE
            comparison.percentageChange.compareTo(BigDecimal.ZERO) > 0 -> MetricComparisonState.INCREASE
            comparison.percentageChange.compareTo(BigDecimal.ZERO) < 0 -> MetricComparisonState.DECREASE
            else -> MetricComparisonState.NO_CHANGE
        }
        
        return DashboardMetricUiModel(
            value = comparison.current,
            previousValue = comparison.previous,
            absoluteChange = comparison.absoluteChange,
            percentageChange = comparison.percentageChange,
            comparisonState = state
        )
    }
}

private object EmptyIngredientRepository : IngredientRepository {
    override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = flowOf(emptyList<com.miara.cuentame.core.model.ingredient.Ingredient>())
    override suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean) = emptyList<com.miara.cuentame.core.model.ingredient.Ingredient>()
    override fun observeIngredient(id: com.miara.cuentame.core.common.ids.IngredientId) = flowOf<com.miara.cuentame.core.model.ingredient.Ingredient?>(null)
    override suspend fun getById(id: com.miara.cuentame.core.common.ids.IngredientId) = null
    override suspend fun getUnitOption(id: com.miara.cuentame.core.common.ids.IngredientUnitOptionId) = null
    override suspend fun updateIngredient(command: com.miara.cuentame.core.domain.repository.UpdateIngredientCommand) = Unit
    override suspend fun archive(id: com.miara.cuentame.core.common.ids.IngredientId, at: java.time.Instant) = Unit
    override fun observeUnitOptions(ingredientId: com.miara.cuentame.core.common.ids.IngredientId, includeArchived: Boolean) = flowOf(emptyList<com.miara.cuentame.core.model.ingredient.IngredientUnitOption>())
    override suspend fun getUnitOptions(ingredientId: com.miara.cuentame.core.common.ids.IngredientId, includeArchived: Boolean) = emptyList<com.miara.cuentame.core.model.ingredient.IngredientUnitOption>()
    override suspend fun addStandardUnitOption(command: com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand) = Unit
    override suspend fun addPackageUnitOption(command: com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand) = Unit
    override suspend fun updatePackageUnitOption(command: com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand) = Unit
    override suspend fun setDefaultCountOption(ingredientId: com.miara.cuentame.core.common.ids.IngredientId, optionId: com.miara.cuentame.core.common.ids.IngredientUnitOptionId) = Unit
    override suspend fun setDefaultPurchaseOption(ingredientId: com.miara.cuentame.core.common.ids.IngredientId, optionId: com.miara.cuentame.core.common.ids.IngredientUnitOptionId) = Unit
    override suspend fun archiveUnitOption(id: com.miara.cuentame.core.common.ids.IngredientUnitOptionId, at: java.time.Instant) = Unit
    override suspend fun createIngredientWithBaseOption(ingredient: com.miara.cuentame.core.model.ingredient.Ingredient, baseOption: com.miara.cuentame.core.model.ingredient.IngredientUnitOption, additionalOptions: List<com.miara.cuentame.core.model.ingredient.IngredientUnitOption>) = Unit
}

private object EmptyAreaRepository : InventoryAreaRepository {
    override fun observeActiveAreas() = flowOf(emptyList<com.miara.cuentame.core.model.inventory.InventoryArea>())
    override fun observeAllAreas() = observeActiveAreas()
    override suspend fun getById(id: InventoryAreaId) = null
    override suspend fun save(area: com.miara.cuentame.core.model.inventory.InventoryArea) = Unit
    override suspend fun archive(id: InventoryAreaId, at: java.time.Instant) = Unit
    override suspend fun reorder(ids: List<InventoryAreaId>) = Unit
}

private object EmptyStockCountRepository : StockCountRepository {
    override fun observeCounts(filter: com.miara.cuentame.core.domain.repository.StockCountFilter) = flowOf(emptyList<com.miara.cuentame.core.domain.repository.StockCountSummary>())
    override fun observeCount(id: com.miara.cuentame.core.common.ids.StockCountId) = flowOf<com.miara.cuentame.core.domain.repository.StockCountDetails?>(null)
    override fun observeCountArea(id: com.miara.cuentame.core.common.ids.StockCountAreaId) = flowOf<com.miara.cuentame.core.domain.repository.StockCountAreaDetails?>(null)
    override fun observeHasCompletedCount(restaurantId: RestaurantId) = flowOf(false)
    override suspend fun getCountedIngredientIds(countId: com.miara.cuentame.core.common.ids.StockCountId, areaId: InventoryAreaId) = emptySet<com.miara.cuentame.core.common.ids.IngredientId>()
    override suspend fun getDraftAreaIds(restaurantId: RestaurantId) = emptySet<InventoryAreaId>()
    override suspend fun getItemOrder(areaId: InventoryAreaId) = emptyList<com.miara.cuentame.core.common.ids.IngredientId>()
    override suspend fun saveItemOrder(areaId: InventoryAreaId, ingredientIds: List<com.miara.cuentame.core.common.ids.IngredientId>) = Unit
    override suspend fun start(command: com.miara.cuentame.core.domain.repository.StartStockCountCommand) = com.miara.cuentame.core.common.ids.StockCountId("")
    override suspend fun updateDraft(command: com.miara.cuentame.core.domain.repository.UpdateStockCountDraftCommand) = Unit
    override suspend fun saveLine(command: com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand): com.miara.cuentame.core.model.count.StockCountLine = throw UnsupportedOperationException()
    override suspend fun deleteLine(countId: com.miara.cuentame.core.common.ids.StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId, lineId: com.miara.cuentame.core.common.ids.StockCountLineId) = Unit
    override suspend fun completeArea(countId: com.miara.cuentame.core.common.ids.StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId) = Unit
    override suspend fun reopenArea(countId: com.miara.cuentame.core.common.ids.StockCountId, countAreaId: com.miara.cuentame.core.common.ids.StockCountAreaId) = Unit
    override suspend fun deleteDraft(countId: com.miara.cuentame.core.common.ids.StockCountId) = Unit
    override suspend fun completeCount(countId: com.miara.cuentame.core.common.ids.StockCountId) = Unit
    override suspend fun findDrift(countId: com.miara.cuentame.core.common.ids.StockCountId) = emptyList<com.miara.cuentame.core.domain.repository.StockCountDriftItem>()
    override suspend fun reconfirmLine(countId: com.miara.cuentame.core.common.ids.StockCountId, lineId: com.miara.cuentame.core.common.ids.StockCountLineId) = Unit
    override suspend fun voidCount(countId: com.miara.cuentame.core.common.ids.StockCountId) = Unit
    override suspend fun getExportRows(countId: com.miara.cuentame.core.common.ids.StockCountId) = emptyList<com.miara.cuentame.core.domain.repository.StockCountExportRow>()
}
