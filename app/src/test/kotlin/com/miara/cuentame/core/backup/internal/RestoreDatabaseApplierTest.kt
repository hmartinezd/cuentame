package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import androidx.room.withTransaction
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.dao.RestoreDao
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.database.backup.BackupSnapshot
import com.miara.cuentame.core.model.backup.BackupManifest
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
        coEvery { restoreDao.insertMenuRecipes(any()) } just Runs
        coEvery { restoreDao.insertMenuRecipeComponents(any()) } just Runs
        coEvery { restoreDao.insertPreparationRecipes(any()) } just Runs
        coEvery { restoreDao.insertPreparationRecipeComponents(any()) } just Runs
        coEvery { restoreDao.insertProductionBatches(any()) } just Runs
        coEvery { restoreDao.insertProductionBatchComponents(any()) } just Runs
        coEvery { restoreDao.insertPurchaseReceipts(any()) } just Runs
        coEvery { restoreDao.insertPurchaseLines(any()) } just Runs
        coEvery { restoreDao.insertStockCounts(any()) } just Runs
        coEvery { restoreDao.insertStockCountAreas(any()) } just Runs
        coEvery { restoreDao.insertStockCountLines(any()) } just Runs
        coEvery { restoreDao.insertStockCountItemOrder(any()) } just Runs
        coEvery { restoreDao.insertWasteEvents(any()) } just Runs
        coEvery { restoreDao.insertInventoryMovements(any()) } just Runs
        coEvery { restoreDao.insertInventoryBalanceProjections(any()) } just Runs
        coEvery { restoreDao.insertIngredientCostProjections(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceOcrResults(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceOcrPages(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceParseResults(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceParsedLines(any()) } just Runs
        coEvery { restoreDao.insertSupplierItemMappings(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceLineMatches(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceDraftApplications(any()) } just Runs
        coEvery { restoreDao.insertPurchaseInvoiceLineOrigins(any()) } just Runs

        val transactionSlot = slot<suspend () -> Any?>()
        coEvery { database.withTransaction(capture(transactionSlot)) } coAnswers {
            transactionSlot.captured.invoke()
        }

        // Mock verification call
        coEvery { backupDao.createGlobalSnapshot() } returns createEmptyRawSnapshot()
        
        applier = RoomRestoreDatabaseApplier(database, backupDao, restoreDao)
    }

    @Test
    fun `replaceWith performs ordered clear and insert`() = runTest {
        val snapshot = createMinimalSnapshot()
        val manifest = mockk<BackupManifest>(relaxed = true) {
            every { attachments } returns emptyList()
        }
        
        applier.replaceWithBackup(snapshot, manifest)
        
        coVerifyOrder {
            restoreDao.clearAllInOrder()
            restoreDao.insertRestaurants(any())
            restoreDao.insertInventoryAreas(any())
            restoreDao.insertIngredientCategories(any())
            restoreDao.insertUnits(any())
            restoreDao.insertSuppliers(any())
            restoreDao.insertIngredients(any())
            restoreDao.insertIngredientUnitOptions(any())
            restoreDao.insertMenuRecipes(any())
            restoreDao.insertMenuRecipeComponents(any())
            restoreDao.insertPreparationRecipes(any())
            restoreDao.insertPreparationRecipeComponents(any())
            restoreDao.insertProductionBatches(any())
            restoreDao.insertProductionBatchComponents(any())
            restoreDao.insertPurchaseReceipts(any())
            restoreDao.insertPurchaseLines(any())
            restoreDao.insertStockCounts(any())
            restoreDao.insertStockCountAreas(any())
            restoreDao.insertStockCountLines(any())
            restoreDao.insertStockCountItemOrder(any())
            restoreDao.insertWasteEvents(any())
            restoreDao.insertInventoryMovements(any())
            restoreDao.insertInventoryBalanceProjections(any())
            restoreDao.insertIngredientCostProjections(any())
            restoreDao.insertPurchaseInvoiceOcrResults(any())
            restoreDao.insertPurchaseInvoiceOcrPages(any())
            restoreDao.insertPurchaseInvoiceParseResults(any())
            restoreDao.insertPurchaseInvoiceParsedLines(any())
            restoreDao.insertSupplierItemMappings(any())
            restoreDao.insertPurchaseInvoiceLineMatches(any())
            restoreDao.insertPurchaseInvoiceDraftApplications(any())
            restoreDao.insertPurchaseInvoiceLineOrigins(any())
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

    private fun createEmptyRawSnapshot() = BackupSnapshot(
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
        ingredientCostProjections = emptyList(),
        preparationRecipes = emptyList(),
        preparationRecipeComponents = emptyList(),
        menuRecipes = emptyList(),
        menuRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList(),
        purchaseInvoiceOcrResults = emptyList(),
        purchaseInvoiceOcrPages = emptyList(),
        purchaseInvoiceParseResults = emptyList(),
        purchaseInvoiceParsedLines = emptyList(),
        supplierItemMappings = emptyList(),
        purchaseInvoiceLineMatches = emptyList(),
        purchaseInvoiceDraftApplications = emptyList(),
        purchaseInvoiceLineOrigins = emptyList()
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
        ingredientCostProjections = emptyList(),
        preparationRecipes = emptyList(),
        preparationRecipeComponents = emptyList(),
        menuRecipes = emptyList(),
        menuRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList(),
        purchaseInvoiceOcrResults = emptyList(),
        purchaseInvoiceOcrPages = emptyList(),
        purchaseInvoiceParseResults = emptyList(),
        purchaseInvoiceParsedLines = emptyList(),
        supplierItemMappings = emptyList(),
        purchaseInvoiceLineMatches = emptyList(),
        purchaseInvoiceDraftApplications = emptyList(),
        purchaseInvoiceLineOrigins = emptyList()
    )
}
