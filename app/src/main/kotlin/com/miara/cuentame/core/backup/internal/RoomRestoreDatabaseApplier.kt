package com.miara.cuentame.core.backup.internal

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.BackupMapper
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
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
        try {
            val entitySnapshot = backupDao.createGlobalSnapshot()
            val dto = BackupMapper.mapToDto(entitySnapshot, emptyMap())
            
            val purchasePaths = entitySnapshot.purchaseReceipts.associate { it.id to it.attachmentPath }
            val purchaseNames = entitySnapshot.purchaseReceipts.associate { it.id to it.attachmentDisplayName }
            val wastePaths = entitySnapshot.wasteEvents.associate { it.id to it.attachmentPath }
            val wasteNames = entitySnapshot.wasteEvents.associate { it.id to it.attachmentDisplayName }
            
            return RestoreDatabaseRollbackSnapshot(
                snapshot = dto,
                purchaseReceiptAttachmentPaths = purchasePaths,
                purchaseReceiptAttachmentDisplayNames = purchaseNames,
                wasteEventAttachmentPaths = wastePaths,
                wasteEventAttachmentDisplayNames = wasteNames,
                attachmentInventory = emptyList() // Will be populated by coordinator during capture
            )
        } catch (e: Exception) {
            throw e
        }
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
                rollbackDisplayNames = emptyMap(),
                rollbackWastePaths = emptyMap(),
                rollbackWasteDisplayNames = emptyMap()
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
                rollbackDisplayNames = rollback.purchaseReceiptAttachmentDisplayNames,
                rollbackWastePaths = rollback.wasteEventAttachmentPaths,
                rollbackWasteDisplayNames = rollback.wasteEventAttachmentDisplayNames
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
            // Validate sanitized display name
            PurchaseAttachmentLocation.validateSegment(att.displayName, "displayName")
            
            for (ref in att.referencedBy) {
                // Validate record ID segment safety
                PurchaseAttachmentLocation.validateSegment(ref.recordId, "recordId")
                
                val livePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> PurchaseAttachmentLocation.buildRelativeLocation(
                        PurchaseReceiptId(ref.recordId), 
                        att.displayName
                    )
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> throw IllegalArgumentException("Unsupported record type in manifest: ${ref.recordType}")
                }
                
                val key = ref.recordType to ref.recordId
                if (mapping.containsKey(key)) {
                    throw IllegalStateException("Duplicate attachment reference for $key")
                }
                mapping[key] = livePath
            }
        }
        return mapping
    }

    private suspend fun insertSnapshot(
        snapshot: BackupSnapshotDto,
        useOriginalPaths: Boolean,
        attachmentMapping: Map<Pair<String, String>, String>,
        rollbackPaths: Map<String, String?>,
        rollbackDisplayNames: Map<String, String?>,
        rollbackWastePaths: Map<String, String?>,
        rollbackWasteDisplayNames: Map<String, String?>
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
                entity.copy(
                    attachmentPath = rollbackPaths[dto.id],
                    attachmentDisplayName = rollbackDisplayNames[dto.id]
                )
            } else {
                val livePath = dto.attachmentId?.let { 
                    attachmentMapping["PURCHASE_RECEIPT" to dto.id] 
                        ?: throw IllegalStateException("Missing expected attachment mapping for purchase ${dto.id}")
                }
                entity.copy(
                    attachmentPath = livePath,
                    attachmentDisplayName = dto.attachmentDisplayName
                )
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
                entity.copy(
                    attachmentPath = rollbackWastePaths[dto.id],
                    attachmentDisplayName = rollbackWasteDisplayNames[dto.id]
                )
            } else {
                val livePath = dto.attachmentId?.let { 
                    attachmentMapping["WASTE_EVENT" to dto.id]
                        ?: throw IllegalStateException("Missing expected attachment mapping for waste ${dto.id}")
                }
                entity.copy(
                    attachmentPath = livePath,
                    attachmentDisplayName = dto.attachmentDisplayName
                )
            }
        }
        restoreDao.insertWasteEvents(waste)
        
        restoreDao.insertInventoryMovements(snapshot.inventoryMovements.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertInventoryBalanceProjections(snapshot.inventoryBalanceProjections.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertIngredientCostProjections(snapshot.ingredientCostProjections.map { BackupMapper.run { it.toEntity() } })

        restoreDao.insertPurchaseInvoiceOcrResults(snapshot.purchaseInvoiceOcrResults.map { BackupMapper.run { it.toEntity() } })
        restoreDao.insertPurchaseInvoiceOcrPages(snapshot.purchaseInvoiceOcrPages.map { BackupMapper.run { it.toEntity() } })
    }

    private suspend fun verifySnapshot(expected: BackupSnapshotDto, manifest: BackupManifest): Boolean {
        val current = backupDao.createGlobalSnapshot()
        
        val livePathToAttachmentId = mutableMapOf<String, String>()
        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                val livePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> PurchaseAttachmentLocation.buildRelativeLocation(
                        PurchaseReceiptId(ref.recordId), 
                        att.displayName
                    )
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> throw IllegalArgumentException("Unsupported record type in manifest: ${ref.recordType}")
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
        val currentPurchaseNames = current.purchaseReceipts.associate { it.id to it.attachmentDisplayName }
        val currentWastePaths = current.wasteEvents.associate { it.id to it.attachmentPath }
        val currentWasteNames = current.wasteEvents.associate { it.id to it.attachmentDisplayName }
        
        return currentPurchasePaths == expected.purchaseReceiptAttachmentPaths &&
               currentPurchaseNames == expected.purchaseReceiptAttachmentDisplayNames &&
               currentWastePaths == expected.wasteEventAttachmentPaths &&
               currentWasteNames == expected.wasteEventAttachmentDisplayNames
    }
}
