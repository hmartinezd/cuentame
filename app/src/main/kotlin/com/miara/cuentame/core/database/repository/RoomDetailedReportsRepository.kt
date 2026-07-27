package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.model.InventoryValuationRow
import com.miara.cuentame.core.database.model.PurchaseSpendRow
import com.miara.cuentame.core.database.model.WasteValueRow
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.model.dashboard.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

class RoomDetailedReportsRepository @Inject constructor(
    private val inventoryProjectionDao: InventoryProjectionDao,
    private val purchaseDao: PurchaseDao,
    private val movementDao: InventoryMovementDao
) : DetailedReportsRepository {

    private val mathContext = MathContext.DECIMAL128

    override fun observeInventoryDetails(restaurantId: RestaurantId): Flow<InventoryDetailReport> {
        return inventoryProjectionDao.observeValuationRows(restaurantId.value).map { rows ->
            val items = rows.groupBy { it.ingredientId }.map { (ingId, ingRows) ->
                val first = ingRows.first()
                val totalQuantity = ingRows.sumOf { BigDecimal(it.quantityBase) }
                val avgCost = first.averageUnitCostBase?.let { BigDecimal(it) }
                val value = avgCost?.let { totalQuantity.multiply(it, mathContext) }
                
                InventoryDetailItem(
                    ingredientId = IngredientId(ingId),
                    ingredientName = first.ingredientName,
                    baseUnitSymbol = first.baseUnitSymbol,
                    totalQuantityBase = totalQuantity,
                    currentAverageCost = avgCost,
                    currentInventoryValue = value,
                    stockedAreaCount = ingRows.count { BigDecimal(it.quantityBase).compareTo(BigDecimal.ZERO) != 0 },
                    negativeAreaBalanceCount = ingRows.count { BigDecimal(it.quantityBase).compareTo(BigDecimal.ZERO) < 0 },
                    isMissingCost = avgCost == null
                )
            }.sortedWith(
                compareBy<InventoryDetailItem> { !it.isMissingCost }
                    .thenByDescending { it.negativeAreaBalanceCount > 0 }
                    .thenByDescending { it.currentInventoryValue ?: BigDecimal.ZERO }
                    .thenBy { it.ingredientName }
                    .thenBy { it.ingredientId.value }
            )

            InventoryDetailReport(
                rows = items,
                totalValue = items.sumOf { it.currentInventoryValue ?: BigDecimal.ZERO },
                recordCount = items.size,
                valuedIngredientCount = items.count { it.currentInventoryValue != null },
                stockedIngredientCount = items.size, // Grouping by ingredientId from valuation rows only includes stocked
                missingCostCount = items.count { it.isMissingCost },
                negativeBalanceCount = items.count { it.totalQuantityBase.compareTo(BigDecimal.ZERO) < 0 }
            )
        }
    }

    override fun observePurchaseDetails(restaurantId: RestaurantId, period: ReportingPeriod): Flow<PurchaseDetailReport> {
        return purchaseDao.observeSpendRows(
            restaurantId.value,
            period.startInclusive.toEpochMilli(),
            period.endExclusive.toEpochMilli()
        ).map { rows ->
            val items = rows.groupBy { it.receiptId }.map { (receiptId, receiptRows) ->
                val first = receiptRows.first()
                PurchaseDetailItem(
                    purchaseId = PurchaseReceiptId(receiptId),
                    purchaseDate = java.time.Instant.ofEpochMilli(first.purchaseDate),
                    postedAt = first.postedAt?.let { java.time.Instant.ofEpochMilli(it) },
                    supplierName = first.supplierName,
                    lineCount = receiptRows.size,
                    total = receiptRows.sumOf { BigDecimal(it.lineTotal) }
                )
            }.sortedWith(
                compareByDescending<PurchaseDetailItem> { it.purchaseDate }
                    .thenBy { it.purchaseId.value }
            )

            PurchaseDetailReport(
                rows = items,
                period = period,
                totalSpend = items.sumOf { it.total },
                recordCount = items.size
            )
        }
    }

    override fun observeWasteDetails(restaurantId: RestaurantId, period: ReportingPeriod): Flow<WasteDetailReport> {
        return movementDao.observeWasteValueRows(
            restaurantId.value,
            period.startInclusive.toEpochMilli(),
            period.endExclusive.toEpochMilli()
        ).map { rows ->
            val items = rows.map { row ->
                WasteDetailItem(
                    wasteEventId = WasteEventId(row.wasteEventId),
                    ingredientId = IngredientId(row.ingredientId),
                    ingredientName = row.ingredientName,
                    areaName = row.areaName,
                    reason = row.reason,
                    timestamp = java.time.Instant.ofEpochMilli(row.timestamp),
                    quantityBase = BigDecimal(row.quantityBase).abs(),
                    baseUnitSymbol = row.baseUnitSymbol,
                    historicalValue = row.totalValueSnapshot?.let { BigDecimal(it).abs() } ?: BigDecimal.ZERO,
                    notes = row.notes
                )
            }.sortedWith(
                compareByDescending<WasteDetailItem> { it.timestamp }
                    .thenBy { it.wasteEventId.value }
            )

            WasteDetailReport(
                rows = items,
                period = period,
                totalWasteValue = items.sumOf { it.historicalValue },
                recordCount = items.size
            )
        }
    }

    private inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal {
        var sum = BigDecimal.ZERO
        for (element in this) {
            sum = sum.add(selector(element))
        }
        return sum
    }
}
