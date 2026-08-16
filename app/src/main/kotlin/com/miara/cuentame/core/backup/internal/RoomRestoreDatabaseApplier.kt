package com.miara.cuentame.core.backup.internal

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.platform.*
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
        restoreDao.insertRestaurants(snapshot.restaurants.map { it.toEntity() })
        restoreDao.insertInventoryAreas(snapshot.inventoryAreas.map { it.toEntity() })
        restoreDao.insertIngredientCategories(snapshot.ingredientCategories.map { it.toEntity() })
        restoreDao.insertUnits(snapshot.units.map { it.toEntity() })
        restoreDao.insertSuppliers(snapshot.suppliers.map { it.toEntity() })
        restoreDao.insertIngredients(snapshot.ingredients.map { it.toEntity() })
        restoreDao.insertIngredientUnitOptions(snapshot.ingredientUnitOptions.map { it.toEntity() })
        restoreDao.insertMenuRecipes(snapshot.menuRecipes.map { it.toEntity() })
        restoreDao.insertMenuRecipeComponents(snapshot.menuRecipeComponents.map { it.toEntity() })
        restoreDao.insertMenus(snapshot.menus.map { it.toEntity() })
        restoreDao.insertMenuCategories(snapshot.menuCategories.map { it.toEntity() })
        restoreDao.insertMenuPlacements(snapshot.menuPlacements.map { it.toEntity() })
        restoreDao.insertMenuPublications(snapshot.menuPublications.map { it.toEntity() })
        restoreDao.insertMenuPublicationCategories(snapshot.menuPublicationCategories.map { it.toEntity() })
        restoreDao.insertMenuPublicationItems(snapshot.menuPublicationItems.map { it.toEntity() })
        restoreDao.insertMenuPublicationItemComponents(snapshot.menuPublicationItemComponents.map { it.toEntity() })
        if(snapshot.salesImports.isNotEmpty())restoreDao.insertSalesImports(snapshot.salesImports.map { it.toEntity() })
        if(snapshot.importedSaleTransactions.isNotEmpty())restoreDao.insertImportedSaleTransactions(snapshot.importedSaleTransactions.map { it.toEntity() })
        if(snapshot.importedSaleLines.isNotEmpty())restoreDao.insertImportedSaleLines(snapshot.importedSaleLines.map { it.toEntity() })
        if(snapshot.salesImportTransactionRefs.isNotEmpty())restoreDao.insertSalesImportTransactionRefs(snapshot.salesImportTransactionRefs.map { it.toEntity() })
        restoreDao.insertPreparationRecipes(snapshot.preparationRecipes.map { it.toEntity() })
        restoreDao.insertPreparationRecipeComponents(snapshot.preparationRecipeComponents.map { it.toEntity() })
        restoreDao.insertProductionBatches(snapshot.productionBatches.map { it.toEntity() })
        restoreDao.insertProductionBatchComponents(snapshot.productionBatchComponents.map { it.toEntity() })

        val receipts = snapshot.purchaseReceipts.map { dto ->
            val entity = dto.toEntity()
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
        
        restoreDao.insertPurchaseLines(snapshot.purchaseLines.map { it.toEntity() })
        restoreDao.insertStockCounts(snapshot.stockCounts.map { it.toEntity() })
        restoreDao.insertStockCountAreas(snapshot.stockCountAreas.map { it.toEntity() })
        restoreDao.insertStockCountLines(snapshot.stockCountLines.map { it.toEntity() })
        restoreDao.insertStockCountItemOrder(snapshot.stockCountItemOrder.map { it.toEntity() })
        
        val waste = snapshot.wasteEvents.map { dto ->
            val entity = dto.toEntity()
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
        
        restoreDao.insertInventoryMovements(snapshot.inventoryMovements.map { it.toEntity() })
        restoreDao.insertInventoryBalanceProjections(snapshot.inventoryBalanceProjections.map { it.toEntity() })
        restoreDao.insertIngredientCostProjections(snapshot.ingredientCostProjections.map { it.toEntity() })

        restoreDao.insertPurchaseInvoiceOcrResults(snapshot.purchaseInvoiceOcrResults.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceOcrPages(snapshot.purchaseInvoiceOcrPages.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceParseResults(snapshot.purchaseInvoiceParseResults.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceParsedLines(snapshot.purchaseInvoiceParsedLines.map { it.toEntity() })
        restoreDao.insertSupplierItemMappings(snapshot.supplierItemMappings.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceLineMatches(snapshot.purchaseInvoiceLineMatches.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceDraftApplications(snapshot.purchaseInvoiceDraftApplications.map { it.toEntity() })
        restoreDao.insertPurchaseInvoiceLineOrigins(snapshot.purchaseInvoiceLineOrigins.map { it.toEntity() })
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
