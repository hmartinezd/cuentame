package com.miara.cuentame.core.backup.internal

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.BackupMapper
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.dao.RestoreDao
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
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

    override suspend fun replaceWithBackup(snapshot: BackupSnapshotDto) {
        // Assert no attachments in incoming backup
        require(snapshot.purchaseReceipts.all { it.attachmentId == null })
        require(snapshot.wasteEvents.all { it.attachmentId == null })

        database.withTransaction {
            restoreDao.clearAllInOrder()
            insertSnapshot(snapshot, useOriginalPaths = false, rollbackPaths = emptyMap(), rollbackWastePaths = emptyMap())
            
            // Verification inside transaction
            if (!verifySnapshot(snapshot)) {
                throw IllegalStateException("Database verification failed after restore")
            }
        }
    }

    override suspend fun restoreRollback(rollback: RestoreDatabaseRollbackSnapshot) {
        database.withTransaction {
            restoreDao.clearAllInOrder()
            insertSnapshot(
                rollback.snapshot, 
                useOriginalPaths = true, 
                rollbackPaths = rollback.purchaseReceiptAttachmentPaths,
                rollbackWastePaths = rollback.wasteEventAttachmentPaths
            )
            
            if (!verifyRollback(rollback)) {
                throw IllegalStateException("Database verification failed after rollback")
            }
        }
    }

    override suspend fun verifyMatchesBackup(snapshot: BackupSnapshotDto): Boolean {
        return verifySnapshot(snapshot)
    }

    override suspend fun verifyMatchesRollback(rollback: RestoreDatabaseRollbackSnapshot): Boolean {
        return verifyRollback(rollback)
    }

    private suspend fun insertSnapshot(
        snapshot: BackupSnapshotDto,
        useOriginalPaths: Boolean,
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
        
        val receipts = snapshot.purchaseReceipts.map { dto ->
            val entity = BackupMapper.run { dto.toEntity() }
            if (useOriginalPaths) {
                entity.copy(attachmentPath = rollbackPaths[dto.id])
            } else {
                entity.copy(attachmentPath = null)
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
                entity.copy(attachmentPath = null)
            }
        }
        restoreDao.insertWasteEvents(waste)
        
        restoreDao.insertInventoryMovements(snapshot.inventoryMovements.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertInventoryBalanceProjections(snapshot.inventoryBalanceProjections.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredientCostProjections(snapshot.ingredientCostProjections.map { BackupMapper.run { it.toEntity() } })
    }

    private suspend fun verifySnapshot(expected: BackupSnapshotDto): Boolean {
        val current = backupDao.createGlobalSnapshot()
        val currentDto = BackupMapper.mapToDto(current, emptyMap())
        
        // Verify identity and counts
        if (currentDto.restaurants.size != expected.restaurants.size) return false
        if (currentDto.restaurants != expected.restaurants) return false
        
        // Detailed check for all tables
        return currentDto == expected && current.purchaseReceipts.all { it.attachmentPath == null } && current.wasteEvents.all { it.attachmentPath == null }
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
