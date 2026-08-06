package com.miara.cuentame.core.backup.internal

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.BackupMapper
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.dao.RestoreDao
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRestoreDatabaseApplier @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val backupDao: BackupDao,
    private val restoreDao: RestoreDao
) : RestoreDatabaseApplier {

    override suspend fun hasExistingAttachmentReferences(): Boolean {
        val receiptsWithAttachments = backupDao.getAllPurchaseReceipts().any { !it.attachmentPath.isNullOrBlank() }
        val wasteWithAttachments = backupDao.getAllWasteEvents().any { !it.attachmentPath.isNullOrBlank() }
        return receiptsWithAttachments || wasteWithAttachments
    }

    override suspend fun captureRollbackSnapshot(): RestoreDatabaseRollbackSnapshot {
        val entitySnapshot = backupDao.createGlobalSnapshot()
        val dto = BackupMapper.mapToDto(entitySnapshot, emptyMap())
        
        val purchasePaths = entitySnapshot.purchaseReceipts.associate { it.id to it.attachmentPath }
        val wastePaths = entitySnapshot.wasteEvents.associate { it.id to it.attachmentPath }
        
        return RestoreDatabaseRollbackSnapshot(
            snapshot = dto,
            purchaseReceiptAttachmentPaths = purchasePaths,
            wasteEventAttachmentPaths = wastePaths
        )
    }

    override suspend fun replaceWithBackup(snapshot: BackupSnapshotDto, manifest: BackupManifest) {
        val attachmentMapping = buildAttachmentMapping(manifest)
        
        database.withTransaction {
            restoreDao.clearAllInOrder()
            insertSnapshot(
                snapshot = snapshot, 
                useOriginalPaths = false, 
                attachmentMapping = attachmentMapping,
                rollbackPaths = emptyMap(), 
                rollbackWastePaths = emptyMap()
            )
            
            if (!verifySnapshot(snapshot, manifest)) {
                throw IllegalStateException("Database verification failed after restore")
            }
        }
    }

    override suspend fun restoreRollback(rollback: RestoreDatabaseRollbackSnapshot) {
        database.withTransaction {
            restoreDao.clearAllInOrder()
            insertSnapshot(
                snapshot = rollback.snapshot, 
                useOriginalPaths = true, 
                attachmentMapping = emptyMap(),
                rollbackPaths = rollback.purchaseReceiptAttachmentPaths,
                rollbackWastePaths = rollback.wasteEventAttachmentPaths
            )
            
            if (!verifyRollback(rollback)) {
                throw IllegalStateException("Database verification failed after rollback")
            }
        }
    }

    override suspend fun verifyMatchesBackup(snapshot: BackupSnapshotDto, manifest: BackupManifest): Boolean {
        return verifySnapshot(snapshot, manifest)
    }

    override suspend fun verifyMatchesRollback(rollback: RestoreDatabaseRollbackSnapshot): Boolean {
        return verifyRollback(rollback)
    }

    private fun buildAttachmentMapping(manifest: BackupManifest): Map<Pair<String, String>, String> {
        val mapping = mutableMapOf<Pair<String, String>, String>()
        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                val livePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> "attachments/purchases/${ref.recordId}/${att.displayName}"
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> "attachments/other/${att.attachmentId}/${att.displayName}"
                }
                mapping[ref.recordType to ref.recordId] = livePath
            }
        }
        return mapping
    }

    private suspend fun insertSnapshot(
        snapshot: BackupSnapshotDto,
        useOriginalPaths: Boolean,
        attachmentMapping: Map<Pair<String, String>, String>,
        rollbackPaths: Map<String, String?>,
        rollbackWastePaths: Map<String, String?>
    ) {
        restoreDao.insertRestaurants(snapshot.restaurants.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertInventoryAreas(snapshot.inventoryAreas.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredientCategories(snapshot.ingredientCategories.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertUnits(snapshot.units.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertSuppliers(snapshot.suppliers.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredients(snapshot.ingredients.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredientUnitOptions(snapshot.ingredientUnitOptions.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertPreparationRecipes(snapshot.preparationRecipes.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertPreparationRecipeComponents(snapshot.preparationRecipeComponents.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertProductionBatches(snapshot.productionBatches.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertProductionBatchComponents(snapshot.productionBatchComponents.map { BackupMapper.run { it.toEntity() } })

        val receipts = snapshot.purchaseReceipts.map { dto ->
            val entity = BackupMapper.run { dto.toEntity() }
            if (useOriginalPaths) {
                entity.copy(attachmentPath = rollbackPaths[dto.id])
            } else {
                val livePath = dto.attachmentId?.let { attachmentMapping["PURCHASE_RECEIPT" to dto.id] }
                entity.copy(attachmentPath = livePath)
            }
        }
        restoreDao.insertPurchaseReceipts(receipts)
        
        restoreDao.insertPurchaseLines(snapshot.purchaseLines.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertStockCounts(snapshot.stockCounts.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertStockCountAreas(snapshot.stockCountAreas.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertStockCountLines(snapshot.stockCountLines.map { BackupMapper.run { it.toEntity() } })
        
        val waste = snapshot.wasteEvents.map { dto ->
            val entity = BackupMapper.run { dto.toEntity() }
            if (useOriginalPaths) {
                entity.copy(attachmentPath = rollbackWastePaths[dto.id])
            } else {
                val livePath = dto.attachmentId?.let { attachmentMapping["WASTE_EVENT" to dto.id] }
                entity.copy(attachmentPath = livePath)
            }
        }
        restoreDao.insertWasteEvents(waste)
        
        restoreDao.insertInventoryMovements(snapshot.inventoryMovements.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertInventoryBalanceProjections(snapshot.inventoryBalanceProjections.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredientCostProjections(snapshot.ingredientCostProjections.map { BackupMapper.run { it.toEntity() } })
    }

    private suspend fun verifySnapshot(expected: BackupSnapshotDto, manifest: BackupManifest): Boolean {
        val current = backupDao.createGlobalSnapshot()
        
        val livePathToAttachmentId = mutableMapOf<String, String>()
        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                val livePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> "attachments/purchases/${ref.recordId}/${att.displayName}"
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> "attachments/other/${att.attachmentId}/${att.displayName}"
                }
                livePathToAttachmentId[livePath] = att.attachmentId
            }
        }
        
        val currentDto = BackupMapper.mapToDto(current, livePathToAttachmentId)
        return currentDto == expected
    }

    private suspend fun verifyRollback(expected: RestoreDatabaseRollbackSnapshot): Boolean {
        val current = backupDao.createGlobalSnapshot()
        val currentDto = BackupMapper.mapToDto(current, emptyMap())
        
        if (currentDto != expected.snapshot) return false
        
        val currentPurchasePaths = current.purchaseReceipts.associate { it.id to it.attachmentPath }
        val currentWastePaths = current.wasteEvents.associate { it.id to it.attachmentPath }
        
        return currentPurchasePaths == expected.purchaseReceiptAttachmentPaths &&
               currentWastePaths == expected.wasteEventAttachmentPaths
    }
}
