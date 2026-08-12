package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.parsePersistedEnum
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.util.ReportDecimalParser
import com.miara.cuentame.core.domain.repository.DetailedReportsRepository
import com.miara.cuentame.core.domain.repository.PurchaseExportRow
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.model.dashboard.*
import com.miara.cuentame.core.model.inventory.WasteReason
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
            val allItems = rows.groupBy { it.ingredientId }.map { (ingId, ingRows) ->
                val first = ingRows.first()
                val totalQuantity = ingRows.sumOf { ReportDecimalParser.parseAny(it.quantityBase) }
                val avgCost = first.averageUnitCostBase?.let { ReportDecimalParser.parseRequiredNonNegative(it) }
                val value = avgCost?.let { totalQuantity.multiply(it, mathContext) }
                
                val negativeAreaBalanceCount = ingRows.count { 
                    ReportDecimalParser.parseAny(it.quantityBase).compareTo(BigDecimal.ZERO) < 0 
                }

                InventoryDetailItem(
                    ingredientId = IngredientId(ingId),
                    ingredientName = first.ingredientName,
                    baseUnitSymbol = first.baseUnitSymbol,
                    totalQuantityBase = totalQuantity,
                    currentAverageCost = avgCost,
                    currentInventoryValue = value,
                    stockedAreaCount = ingRows.count { 
                        ReportDecimalParser.parseAny(it.quantityBase).compareTo(BigDecimal.ZERO) != 0 
                    },
                    negativeAreaBalanceCount = negativeAreaBalanceCount,
                    isMissingCost = totalQuantity.compareTo(BigDecimal.ZERO) != 0 && avgCost == null
                )
            }

            // Row inclusion: aggregate quantity != 0 OR at least one negative area balance
            val includedRows = allItems.filter { 
                it.totalQuantityBase.compareTo(BigDecimal.ZERO) != 0 || it.negativeAreaBalanceCount > 0 
            }.sortedWith(
                compareBy<InventoryDetailItem> { !it.isMissingCost }
                    .thenByDescending { it.negativeAreaBalanceCount > 0 }
                    .thenByDescending { it.currentInventoryValue ?: BigDecimal.ZERO }
                    .thenBy { it.ingredientName }
                    .thenBy { it.ingredientId.value }
            )

            InventoryDetailReport(
                rows = includedRows,
                totalValue = includedRows.sumOf { it.currentInventoryValue ?: BigDecimal.ZERO },
                recordCount = includedRows.size,
                valuedIngredientCount = includedRows.count { 
                    it.totalQuantityBase.compareTo(BigDecimal.ZERO) != 0 && it.currentInventoryValue != null 
                },
                stockedIngredientCount = includedRows.count { 
                    it.totalQuantityBase.compareTo(BigDecimal.ZERO) != 0 
                },
                missingCostCount = includedRows.count { it.isMissingCost },
                negativeBalanceCount = includedRows.map { it.negativeAreaBalanceCount }.sum()
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
                    total = receiptRows.sumOf { ReportDecimalParser.parseRequiredNonNegative(it.lineTotal) }
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
                val reason = parsePersistedEnum(row.reason, WasteReason.UNKNOWN)
                WasteDetailItem(
                    wasteEventId = WasteEventId(row.wasteEventId),
                    ingredientId = IngredientId(row.ingredientId),
                    ingredientName = row.ingredientName,
                    areaName = row.areaName,
                    reason = reason,
                    timestamp = java.time.Instant.ofEpochMilli(row.timestamp),
                    quantityBase = ReportDecimalParser.parseHistoricalSnapshot(row.quantityBase).abs(),
                    baseUnitSymbol = row.baseUnitSymbol,
                    historicalValue = ReportDecimalParser.parseHistoricalSnapshot(row.totalValueSnapshot).abs(),
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

    override fun observePurchaseExportRows(
        restaurantId: RestaurantId,
        period: ReportingPeriod
    ): Flow<List<PurchaseExportRow>> {
        return purchaseDao.observePurchaseExportRows(
            restaurantId.value,
            period.startInclusive.toEpochMilli(),
            period.endExclusive.toEpochMilli()
        )
    }

    private inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal {
        var sum = BigDecimal.ZERO
        for (element in this) {
            sum = sum.add(selector(element))
        }
        return sum
    }
}
