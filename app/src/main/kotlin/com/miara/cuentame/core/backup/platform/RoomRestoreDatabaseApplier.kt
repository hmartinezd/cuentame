package com.miara.cuentame.core.backup.platform

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.backup.platform.BackupMapper.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRestoreDatabaseApplier @Inject constructor(
    private val database: RestaurantInventoryDatabase
) : RestoreDatabaseApplier {

    override suspend fun captureRollbackSnapshot(): BackupSnapshotDto {
        val rawSnapshot = database.backupDao().createGlobalSnapshot()
        // We use the existing mapToDto with empty map for URIs because we want the raw attachmentId stored in entities
        return BackupMapper.mapToDto(rawSnapshot, emptyMap())
    }

    override suspend fun replaceWith(snapshot: BackupSnapshotDto) {
        database.withTransaction {
            val restoreDao = database.restoreDao()
            
            // 1. Delete in FK-safe order (Child -> Parent)
            restoreDao.clearAllInOrder()
            
            // 2. Insert in Parent -> Child order
            restoreDao.insertRestaurants(snapshot.restaurants.map { it.toEntity() })
            restoreDao.insertInventoryAreas(snapshot.inventoryAreas.map { it.toEntity() })
            restoreDao.insertIngredientCategories(snapshot.ingredientCategories.map { it.toEntity() })
            restoreDao.insertUnits(snapshot.units.map { it.toEntity() })
            restoreDao.insertSuppliers(snapshot.suppliers.map { it.toEntity() })
            restoreDao.insertIngredients(snapshot.ingredients.map { it.toEntity() })
            restoreDao.insertIngredientUnitOptions(snapshot.ingredientUnitOptions.map { it.toEntity() })
            restoreDao.insertPurchaseReceipts(snapshot.purchaseReceipts.map { it.toEntity() })
            restoreDao.insertPurchaseLines(snapshot.purchaseLines.map { it.toEntity() })
            restoreDao.insertStockCounts(snapshot.stockCounts.map { it.toEntity() })
            restoreDao.insertStockCountAreas(snapshot.stockCountAreas.map { it.toEntity() })
            restoreDao.insertStockCountLines(snapshot.stockCountLines.map { it.toEntity() })
            restoreDao.insertWasteEvents(snapshot.wasteEvents.map { it.toEntity() })
            restoreDao.insertInventoryMovements(snapshot.inventoryMovements.map { it.toEntity() })
            restoreDao.insertInventoryBalanceProjections(snapshot.inventoryBalanceProjections.map { it.toEntity() })
            restoreDao.insertIngredientCostProjections(snapshot.ingredientCostProjections.map { it.toEntity() })
        }
    }
}
