package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import androidx.room.withTransaction
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.dao.RestoreDao
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RestoreDatabaseApplierTest {

    private val database = mockk<RestaurantInventoryDatabase>()
    private val backupDao = mockk<BackupDao>()
    private val restoreDao = mockk<RestoreDao>()
    private lateinit var applier: RoomRestoreDatabaseApplier

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        
        coEvery { restoreDao.clearAllInOrder() } just Runs
        coEvery { restoreDao.insertRestaurants(any()) } just Runs
        coEvery { restoreDao.insertInventoryAreas(any()) } just Runs
        coEvery { restoreDao.insertIngredientCategories(any()) } just Runs
        coEvery { restoreDao.insertUnits(any()) } just Runs
        coEvery { restoreDao.insertSuppliers(any()) } just Runs
        coEvery { restoreDao.insertIngredients(any()) } just Runs
        coEvery { restoreDao.insertIngredientUnitOptions(any()) } just Runs
        coEvery { restoreDao.insertPurchaseReceipts(any()) } just Runs
        coEvery { restoreDao.insertPurchaseLines(any()) } just Runs
        coEvery { restoreDao.insertStockCounts(any()) } just Runs
        coEvery { restoreDao.insertStockCountAreas(any()) } just Runs
        coEvery { restoreDao.insertStockCountLines(any()) } just Runs
        coEvery { restoreDao.insertWasteEvents(any()) } just Runs
        coEvery { restoreDao.insertInventoryMovements(any()) } just Runs
        coEvery { restoreDao.insertInventoryBalanceProjections(any()) } just Runs
        coEvery { restoreDao.insertIngredientCostProjections(any()) } just Runs

        val transactionSlot = slot<suspend () -> Any?>()
        coEvery { database.withTransaction(capture(transactionSlot)) } coAnswers {
            transactionSlot.captured.invoke()
        }

        // Mock verification call
        coEvery { backupDao.createGlobalSnapshot() } returns createEmptyRawSnapshot()
        
        applier = RoomRestoreDatabaseApplier(database, backupDao, restoreDao)
    }

    @Test
    fun `replaceWithBackup performs ordered clear and insert`() = runTest {
        val snapshot = createMinimalSnapshot()
        
        applier.replaceWithBackup(snapshot)
        
        coVerifyOrder {
            restoreDao.clearAllInOrder()
            restoreDao.insertRestaurants(any())
            restoreDao.insertInventoryAreas(any())
            restoreDao.insertIngredientCategories(any())
            restoreDao.insertUnits(any())
            restoreDao.insertSuppliers(any())
            restoreDao.insertIngredients(any())
            restoreDao.insertIngredientUnitOptions(any())
            restoreDao.insertPurchaseReceipts(any())
            restoreDao.insertPurchaseLines(any())
            restoreDao.insertStockCounts(any())
            restoreDao.insertStockCountAreas(any())
            restoreDao.insertStockCountLines(any())
            restoreDao.insertWasteEvents(any())
            restoreDao.insertInventoryMovements(any())
            restoreDao.insertInventoryBalanceProjections(any())
            restoreDao.insertIngredientCostProjections(any())
        }
    }

    @Test
    fun `captureRollbackSnapshot calls createGlobalSnapshot`() = runTest {
        val rawSnapshot = createEmptyRawSnapshot()
        coEvery { backupDao.createGlobalSnapshot() } returns rawSnapshot
        
        val result = applier.captureRollbackSnapshot()
        
        assertThat(result).isNotNull()
        coVerify { backupDao.createGlobalSnapshot() }
    }

    private fun createEmptyRawSnapshot() = com.miara.cuentame.core.database.backup.BackupSnapshot(
        restaurants = emptyList(),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )

    private fun createMinimalSnapshot() = BackupSnapshotDto(
        restaurants = emptyList(),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )
}
