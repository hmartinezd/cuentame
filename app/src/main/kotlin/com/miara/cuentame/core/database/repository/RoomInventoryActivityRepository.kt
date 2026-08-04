package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.model.InventoryActivityRow
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.model.inventory.InventoryActivityCategory
import com.miara.cuentame.core.model.inventory.InventoryActivityItem
import com.miara.cuentame.core.model.inventory.InventoryActivityQuery
import com.miara.cuentame.core.model.inventory.InventoryActivityRelatedMovementDisplay
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceDisplay
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

class RoomInventoryActivityRepository @Inject constructor(
    private val movementDao: InventoryMovementDao
) : InventoryActivityRepository {

    override fun observeActivity(query: InventoryActivityQuery): Flow<List<InventoryActivityItem>> {
        return movementDao.observeInventoryActivityRows(
            restaurantId = query.restaurantId.value,
            startInclusive = query.startInclusive.toEpochMilli(),
            endExclusive = query.endExclusive.toEpochMilli(),
            ingredientId = query.ingredientId?.value,
            areaId = query.areaId?.value
        ).map { rows ->
            rows.map { it.toActivityItem() }
        }
    }

    override suspend fun getActivityItem(movementId: InventoryMovementId): InventoryActivityItem? {
        return movementDao.getInventoryActivityRow(movementId.value)?.toActivityItem()
    }

    override fun resolveSourceTarget(item: InventoryActivityItem): InventoryActivitySourceTarget {
        val movement = item.movement
        return when (movement.sourceDocumentType) {
            SourceDocumentType.PURCHASE_RECEIPT -> InventoryActivitySourceTarget.Purchase(PurchaseReceiptId(movement.sourceDocumentId))
            SourceDocumentType.WASTE_EVENT -> InventoryActivitySourceTarget.Waste(WasteEventId(movement.sourceDocumentId))
            SourceDocumentType.STOCK_COUNT -> InventoryActivitySourceTarget.StockCount(StockCountId(movement.sourceDocumentId))
            SourceDocumentType.PRODUCTION_BATCH -> InventoryActivitySourceTarget.Production(ProductionBatchId(movement.sourceDocumentId))
            else -> InventoryActivitySourceTarget.Unavailable
        }
    }

    private fun InventoryActivityRow.toActivityItem(): InventoryActivityItem {
        val movementDomain = movement.toDomain()
        
        return InventoryActivityItem(
            movement = movementDomain,
            ingredientName = ingredientName,
            areaName = areaName,
            baseUnitSymbol = baseUnitSymbol,
            sourceDisplay = toSourceDisplay(),
            reversedByMovementId = reversedByMovementId?.let { InventoryMovementId(it) },
            reversalOfDisplay = reversalOfMovementType?.let { type ->
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(movement.reversalOfMovementId!!),
                    category = type.toActivityCategory(),
                    effectiveAt = reversalOfMovementEffectiveAt!!
                )
            },
            reversedByDisplay = reversedByMovementId?.let { id ->
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(id),
                    category = reversedByMovementType!!.toActivityCategory(),
                    effectiveAt = reversedByMovementEffectiveAt!!
                )
            }
        )
    }

    private fun InventoryActivityRow.toSourceDisplay(): InventoryActivitySourceDisplay {
        return when (movement.sourceDocumentType) {
            SourceDocumentType.PURCHASE_RECEIPT.name -> InventoryActivitySourceDisplay(
                title = sourcePurchaseSupplierName?.let { "Purchase from $it" } ?: "Purchase",
                subtitle = sourcePurchaseInvoiceNumber?.let { "Invoice $it" },
                status = null
            )
            SourceDocumentType.WASTE_EVENT.name -> InventoryActivitySourceDisplay(
                title = "Waste — ${sourceWasteReason ?: "Reason unavailable"}",
                subtitle = sourceWasteAreaName,
                status = null
            )
            SourceDocumentType.STOCK_COUNT.name -> InventoryActivitySourceDisplay(
                title = sourceStockCountName ?: "Stock count adjustment",
                subtitle = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withZone(ZoneId.systemDefault())
                    .format(movement.toDomain().effectiveAt),
                status = null
            )
            SourceDocumentType.PRODUCTION_BATCH.name -> InventoryActivitySourceDisplay(
                title = "Production — ${sourceProductionRecipeName ?: "Batch"}",
                subtitle = sourceProductionStatus,
                status = null // Using subtitle for status as per example "Production — Ground Beef \n Posted"
            )
            else -> InventoryActivitySourceDisplay(
                title = "Source unavailable",
                subtitle = null,
                status = null
            )
        }
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
