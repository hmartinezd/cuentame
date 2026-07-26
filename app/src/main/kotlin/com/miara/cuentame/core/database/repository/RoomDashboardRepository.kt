package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.model.*
import com.miara.cuentame.core.domain.repository.DashboardRepository
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.dashboard.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

class RoomDashboardRepository @Inject constructor(
    private val inventoryProjectionDao: InventoryProjectionDao,
    private val purchaseDao: PurchaseDao,
    private val movementDao: InventoryMovementDao,
    private val stockCountDao: StockCountDao,
    private val ingredientDao: IngredientDao,
    private val periodCalculator: ReportingPeriodCalculator
) : DashboardRepository {

    private val mathContext = MathContext.DECIMAL128

    override fun observeDashboard(
        restaurantId: RestaurantId,
        range: DashboardDateRange
    ): Flow<DashboardSnapshot> {
        val currentPeriod = periodCalculator.calculateCurrentPeriod(range)
        val previousPeriod = periodCalculator.calculatePreviousPeriod(range)

        return combine(
            inventoryProjectionDao.observeValuationRows(restaurantId.value),
            purchaseDao.observeSpendRows(restaurantId.value, currentPeriod.startInclusive.toEpochMilli(), currentPeriod.endExclusive.toEpochMilli()),
            purchaseDao.observeSpendRows(restaurantId.value, previousPeriod.startInclusive.toEpochMilli(), previousPeriod.endExclusive.toEpochMilli()),
            movementDao.observeWasteValueRows(restaurantId.value, currentPeriod.startInclusive.toEpochMilli(), currentPeriod.endExclusive.toEpochMilli()),
            movementDao.observeWasteValueRows(restaurantId.value, previousPeriod.startInclusive.toEpochMilli(), previousPeriod.endExclusive.toEpochMilli()),
            stockCountDao.observeCompletedCountLines(restaurantId.value, currentPeriod.startInclusive.toEpochMilli(), currentPeriod.endExclusive.toEpochMilli()),
            ingredientDao.observeActiveIngredientsMissingOptionsCount(restaurantId.value),
            movementDao.observeTopWasteRows(restaurantId.value, currentPeriod.startInclusive.toEpochMilli(), currentPeriod.endExclusive.toEpochMilli()),
            purchaseDao.observeRecentPurchaseActivity(restaurantId.value, 10),
            movementDao.observeRecentWasteActivity(restaurantId.value, 10),
            stockCountDao.observeRecentCountActivity(restaurantId.value, 10)
        ) { args ->
            val valuationRows = args[0] as List<InventoryValuationRow>
            val currentSpendRows = args[1] as List<PurchaseSpendRow>
            val previousSpendRows = args[2] as List<PurchaseSpendRow>
            val currentWasteRows = args[3] as List<WasteValueRow>
            val previousWasteRows = args[4] as List<WasteValueRow>
            val currentCountLines = args[5] as List<CompletedCountLineRow>
            val missingOptionsCount = args[6] as Int
            val topWasteRows = args[7] as List<TopWasteRow>
            val recentPurchases = args[8] as List<RecentPurchaseActivityRow>
            val recentWaste = args[9] as List<RecentWasteActivityRow>
            val recentCounts = args[10] as List<RecentCountActivityRow>

            DashboardSnapshot(
                inventory = calculateInventoryValuation(valuationRows),
                purchases = calculateSpendComparison(currentSpendRows, previousSpendRows),
                waste = calculateWasteComparison(currentWasteRows, previousWasteRows),
                negativeBalanceCount = valuationRows.count { (parseDecimal(it.quantityBase) ?: BigDecimal.ZERO) < BigDecimal.ZERO },
                completedCountCount = currentCountLines.map { it.stockCountId }.distinct().size,
                mostRecentCompletedCountAt = currentCountLines.maxOfOrNull { it.completedAt }?.let { Instant.ofEpochMilli(it) },
                adjustedLineCount = currentCountLines.count { (parseDecimal(it.adjustmentQuantityBase) ?: BigDecimal.ZERO).compareTo(BigDecimal.ZERO) != 0 },
                activeIngredientsMissingOptionsCount = missingOptionsCount,
                topWasteItems = aggregateTopWaste(topWasteRows),
                recentActivity = combineRecentActivity(recentPurchases, recentWaste, recentCounts)
            )
        }
    }

    private fun calculateInventoryValuation(rows: List<InventoryValuationRow>): InventoryValuationSummary {
        val balances = rows.groupBy { it.ingredientId }
            .mapValues { (_, ingRows) ->
                ingRows.sumOf { parseDecimal(it.quantityBase) ?: BigDecimal.ZERO }
            }
        
        val stockedIngredients = balances.filter { it.value.compareTo(BigDecimal.ZERO) != 0 }.keys
        
        var totalValue = BigDecimal.ZERO
        var valuedCount = 0
        var missingCostCount = 0
        
        stockedIngredients.forEach { ingId ->
            val balance = balances[ingId]!!
            val costStr = rows.first { it.ingredientId == ingId }.averageUnitCostBase
            val cost = parseDecimal(costStr)
            
            if (cost != null && cost >= BigDecimal.ZERO) {
                totalValue = totalValue.add(balance.multiply(cost, mathContext))
                valuedCount++
            } else {
                missingCostCount++
            }
        }
        
        return InventoryValuationSummary(
            totalValue = totalValue,
            valuedIngredientCount = valuedCount,
            stockedIngredientCount = stockedIngredients.size,
            missingCostCount = missingCostCount
        )
    }

    private fun calculateSpendComparison(current: List<PurchaseSpendRow>, previous: List<PurchaseSpendRow>): MetricComparison {
        val currentTotal = current.sumOf { parseDecimal(it.lineTotal) ?: BigDecimal.ZERO }
        val previousTotal = previous.sumOf { parseDecimal(it.lineTotal) ?: BigDecimal.ZERO }
        return calculateComparison(currentTotal, previousTotal)
    }

    private fun calculateWasteComparison(current: List<WasteValueRow>, previous: List<WasteValueRow>): MetricComparison {
        val currentTotal = current.sumOf { (parseDecimal(it.totalValueSnapshot) ?: BigDecimal.ZERO).abs() }
        val previousTotal = previous.sumOf { (parseDecimal(it.totalValueSnapshot) ?: BigDecimal.ZERO).abs() }
        return calculateComparison(currentTotal, previousTotal)
    }

    private fun calculateComparison(current: BigDecimal, previous: BigDecimal): MetricComparison {
        val absoluteChange = current.subtract(previous)
        val percentageChange = if (previous.compareTo(BigDecimal.ZERO) != 0) {
            absoluteChange.divide(previous.abs(), mathContext)
                .multiply(BigDecimal("100"), mathContext)
                .setScale(1, RoundingMode.HALF_UP)
        } else {
            null
        }
        return MetricComparison(current, previous, absoluteChange, percentageChange)
    }

    private fun aggregateTopWaste(rows: List<TopWasteRow>): List<WasteReportItem> {
        return rows.groupBy { it.ingredientId }
            .map { (id, ingRows) ->
                val first = ingRows.first()
                WasteReportItem(
                    ingredientId = IngredientId(id),
                    name = first.ingredientName,
                    quantityBase = ingRows.sumOf { (parseDecimal(it.totalQuantityBase) ?: BigDecimal.ZERO).abs() },
                    unitSymbol = first.baseUnitSymbol,
                    totalValue = ingRows.sumOf { (parseDecimal(it.totalWasteValue) ?: BigDecimal.ZERO).abs() },
                    eventCount = ingRows.size
                )
            }
            .sortedWith(
                compareByDescending<WasteReportItem> { it.totalValue }
                    .thenBy { it.name }
                    .thenBy { it.ingredientId.value }
            )
            .take(5)
    }

    private fun combineRecentActivity(
        purchaseLines: List<RecentPurchaseActivityRow>,
        waste: List<RecentWasteActivityRow>,
        counts: List<RecentCountActivityRow>
    ): List<DashboardActivityItem> {
        val items = mutableListOf<DashboardActivityItem>()
        
        purchaseLines.groupBy { it.id }.forEach { (_, lines) ->
            val first = lines.first()
            items.add(DashboardActivityItem(
                id = first.id,
                type = DashboardActivityType.PURCHASE,
                status = first.status,
                timestamp = Instant.ofEpochMilli(first.postedAt),
                description = first.supplierName ?: "Purchase",
                value = lines.sumOf { parseDecimal(it.lineTotal) ?: BigDecimal.ZERO }
            ))
        }
        
        waste.forEach {
            items.add(DashboardActivityItem(
                id = it.id,
                type = DashboardActivityType.WASTE,
                status = it.status,
                timestamp = Instant.ofEpochMilli(it.timestamp),
                description = it.ingredientName,
                value = parseDecimal(it.totalValue)?.abs()
            ))
        }
        
        counts.forEach {
            items.add(DashboardActivityItem(
                id = it.id,
                type = DashboardActivityType.STOCK_COUNT,
                status = it.status,
                timestamp = Instant.ofEpochMilli(it.completedAt),
                description = it.name
            ))
        }
        
        return items.sortedWith(
            compareByDescending<DashboardActivityItem> { it.timestamp }
                .thenBy { it.type.ordinal }
                .thenBy { it.id }
        ).take(10)
    }

    private fun parseDecimal(value: String?): BigDecimal? {
        if (value == null) return null
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.InvalidDecimal
        }
    }
}

// Extension to use sumOf with BigDecimal since it's not in stdlib for sumOf
private inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal {
    var sum = BigDecimal.ZERO
    for (element in this) {
        sum = sum.add(selector(element))
    }
    return sum
}
