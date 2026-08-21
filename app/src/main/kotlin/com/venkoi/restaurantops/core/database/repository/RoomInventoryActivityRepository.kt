package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.common.parsePersistedEnum
import com.venkoi.restaurantops.core.common.parsePersistedEnumOrNull
import com.venkoi.restaurantops.core.common.ids.InventoryMovementId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.common.ids.WasteEventId
import com.venkoi.restaurantops.core.common.ids.ProductionBatchId
import com.venkoi.restaurantops.core.database.dao.InventoryMovementDao
import com.venkoi.restaurantops.core.database.mapper.toDomain
import com.venkoi.restaurantops.core.database.model.InventoryActivityRow
import com.venkoi.restaurantops.core.domain.repository.InventoryActivityRepository
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityItem
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityQuery
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityRelatedMovementDisplay
import com.venkoi.restaurantops.core.model.inventory.InventoryActivitySourceInfo
import com.venkoi.restaurantops.core.model.inventory.InventoryActivitySourceTarget
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import com.venkoi.restaurantops.core.model.inventory.WasteReason
import com.venkoi.restaurantops.core.model.inventory.toInventoryActivityCategory
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
            reversalOfDisplay = reversalOfMovementType?.let { typeStr ->
                val type = parsePersistedEnum(typeStr, InventoryMovementType.UNKNOWN)
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(movement.reversalOfMovementId!!),
                    category = type.toInventoryActivityCategory(),
                    effectiveAt = reversalOfMovementEffectiveAt!!
                )
            },
            reversedByDisplay = reversedByMovementId?.let { id ->
                val type = parsePersistedEnum(reversedByMovementType, InventoryMovementType.UNKNOWN)
                InventoryActivityRelatedMovementDisplay(
                    movementId = InventoryMovementId(id),
                    category = type.toInventoryActivityCategory(),
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
                reason = parsePersistedEnumOrNull(sourceWasteReason, WasteReason.UNKNOWN),
                sourceAreaName = sourceWasteAreaName,
                isResolved = sourceWasteResolvedId != null
            )
            SourceDocumentType.STOCK_COUNT.name -> InventoryActivitySourceInfo.StockCount(
                countName = sourceStockCountName,
                isResolved = sourceStockCountResolvedId != null
            )
            SourceDocumentType.PRODUCTION_BATCH.name -> InventoryActivitySourceInfo.Production(
                recipeName = sourceProductionRecipeName,
                status = parsePersistedEnumOrNull(sourceProductionStatus, DocumentStatus.UNKNOWN),
                isResolved = sourceProductionResolvedId != null
            )
            else -> InventoryActivitySourceInfo.Other(
                sourceDocumentType = parsePersistedEnum(movement.sourceDocumentType, SourceDocumentType.UNKNOWN)
            )
        }
    }
}
