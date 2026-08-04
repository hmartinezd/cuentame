package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.model.InventoryActivityRow
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryActivityCategory
import com.miara.cuentame.core.model.inventory.InventoryActivityItem
import com.miara.cuentame.core.model.inventory.InventoryActivityQuery
import com.miara.cuentame.core.model.inventory.InventoryActivityRelatedMovementDisplay
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceInfo
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.model.inventory.toInventoryActivityCategory
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

    override suspend fun getActivityItem(
        restaurantId: RestaurantId,
        movementId: InventoryMovementId
    ): InventoryActivityItem? {
        return movementDao.getInventoryActivityRow(
            restaurantId = restaurantId.value,
            movementId = movementId.value
        )?.toActivityItem()
    }

    override fun resolveSourceTarget(item: InventoryActivityItem): InventoryActivitySourceTarget {
        val movement = item.movement
        val info = item.sourceInfo
        
        val isResolved = when (info) {
            is InventoryActivitySourceInfo.Purchase -> info.isResolved
            is InventoryActivitySourceInfo.Waste -> info.isResolved
            is InventoryActivitySourceInfo.StockCount -> info.isResolved
            is InventoryActivitySourceInfo.Production -> info.isResolved
            is InventoryActivitySourceInfo.Other -> false
        }
        
        if (!isResolved) return InventoryActivitySourceTarget.Unavailable

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
            sourceInfo = toSourceInfo(),
            reversedByMovementId = reversedByMovementId?.let { InventoryMovementId(it) },
            reversalOfDisplay = reversalOfMovementType?.let { type ->
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(movement.reversalOfMovementId!!),
                    category = type.toInventoryActivityCategory(),
                    effectiveAt = reversalOfMovementEffectiveAt!!
                )
            },
            reversedByDisplay = reversedByMovementId?.let { id ->
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(id),
                    category = reversedByMovementType!!.toInventoryActivityCategory(),
                    effectiveAt = reversedByMovementEffectiveAt!!
                )
            }
        )
    }

    private fun InventoryActivityRow.toSourceInfo(): InventoryActivitySourceInfo {
        return when (movement.sourceDocumentType) {
            SourceDocumentType.PURCHASE_RECEIPT.name -> InventoryActivitySourceInfo.Purchase(
                supplierName = sourcePurchaseSupplierName,
                invoiceNumber = sourcePurchaseInvoiceNumber,
                isResolved = sourcePurchaseResolvedId != null
            )
            SourceDocumentType.WASTE_EVENT.name -> InventoryActivitySourceInfo.Waste(
                reason = sourceWasteReason?.let { reason ->
                    runCatching { WasteReason.valueOf(reason) }.getOrNull()
                },
                sourceAreaName = sourceWasteAreaName,
                isResolved = sourceWasteResolvedId != null
            )
            SourceDocumentType.STOCK_COUNT.name -> InventoryActivitySourceInfo.StockCount(
                countName = sourceStockCountName,
                isResolved = sourceStockCountResolvedId != null
            )
            SourceDocumentType.PRODUCTION_BATCH.name -> InventoryActivitySourceInfo.Production(
                recipeName = sourceProductionRecipeName,
                status = sourceProductionStatus?.let { status ->
                    runCatching { DocumentStatus.valueOf(status) }.getOrNull()
                },
                isResolved = sourceProductionResolvedId != null
            )
            else -> InventoryActivitySourceInfo.Other(
                sourceDocumentType = SourceDocumentType.valueOf(movement.sourceDocumentType)
            )
        }
    }
}
