package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.PurchaseDraftRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

class RoomPurchaseDraftRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val purchaseDao: PurchaseDao
) : PurchaseDraftRepository {

    override fun observeDraftReceipts(): Flow<List<PurchaseReceipt>> {
        return purchaseDao.observeReceiptsByStatus(DocumentStatus.DRAFT.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDraftReceipt(id: PurchaseReceiptId): PurchaseReceipt? {
        val receipt = purchaseDao.getReceiptById(id.value)?.toDomain()
        return if (receipt?.status == DocumentStatus.DRAFT) receipt else null
    }

    override suspend fun getDraftLines(id: PurchaseReceiptId): List<PurchaseLine> {
        // We only want lines for DRAFT receipts
        val receipt = purchaseDao.getReceiptById(id.value)
        if (receipt?.status != DocumentStatus.DRAFT.name) return emptyList()
        
        return purchaseDao.getLinesForReceipt(id.value).map { it.toDomain() }
    }

    override suspend fun saveDraft(receipt: PurchaseReceipt, lines: List<PurchaseLine>) {
        if (receipt.status != DocumentStatus.DRAFT) {
            throw ValidationError.ArchivedReference // Or a new error for status mismatch
        }
        
        // Validate ownership and data integrity
        lines.forEach { line ->
            if (line.purchaseReceiptId != receipt.id) throw ValidationError.InvalidMovementSourceOperation
            if (line.quantityEntered <= BigDecimal.ZERO) throw ValidationError.InvalidDecimal
        }

        database.withTransaction {
            purchaseDao.upsertReceipt(receipt.toEntity())
            // Simple approach for draft: replace all lines
            purchaseDao.deleteLinesForReceipt(receipt.id.value)
            purchaseDao.upsertLines(lines.map { it.toEntity() })
        }
    }

    override suspend fun deleteDraft(id: PurchaseReceiptId) {
        database.withTransaction {
            purchaseDao.deleteLinesForReceipt(id.value)
            purchaseDao.deleteDraftReceipt(id.value)
        }
    }
}
