package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.PurchaseDraftRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        return purchaseDao.getReceiptById(id.value)?.toDomain()
    }

    override suspend fun getDraftLines(id: PurchaseReceiptId): List<PurchaseLine> {
        return purchaseDao.getLinesForReceipt(id.value).map { it.toDomain() }
    }

    override suspend fun saveDraft(receipt: PurchaseReceipt, lines: List<PurchaseLine>) {
        if (receipt.status != DocumentStatus.DRAFT) {
            throw IllegalArgumentException("Only DRAFT receipts can be saved via this repository")
        }
        database.withTransaction {
            purchaseDao.upsertReceipt(receipt.toEntity())
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
