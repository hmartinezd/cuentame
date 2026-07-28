package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private val backupDao = mockk<BackupDao>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val checksumProvider = Sha256ChecksumProvider()

    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = AndroidBackupRepository(
            context,
            backupDao,
            restaurantRepository,
            preferencesRepository,
            timeProvider,
            appVersionProvider,
            checksumProvider
        )
    }

    @Test
    fun backup_roundTrip_richDataset() = runTest {
        val tempFile = File(context.cacheDir, "roundtrip.zip")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        val uri = Uri.fromFile(tempFile)

        // Mock data
        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 1
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)
        
        val restId = com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(restId, "Test Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        // Create dummy file for attachment
        val attachmentFile = File(context.cacheDir, "receipt.jpg")
        attachmentFile.writeText("dummy attachment content")
        val attachmentUri = Uri.fromFile(attachmentFile)

        coEvery { backupDao.createSnapshot("rest-1") } returns BackupSnapshot(
            restaurants = listOf(com.miara.cuentame.core.database.entity.RestaurantEntity("rest-1", "Test Rest", "USD", "en", 0L, 0L, null)),
            inventoryAreas = listOf(com.miara.cuentame.core.database.entity.InventoryAreaEntity("area-1", "rest-1", "Area 1", "area-1", 1, true, 0L, 0L, null)),
            ingredientCategories = emptyList(),
            units = listOf(com.miara.cuentame.core.database.entity.UnitEntity("u1", "Unit", "u", "Dimension", BigDecimal.ONE, true, 1)),
            ingredients = listOf(com.miara.cuentame.core.database.entity.IngredientEntity("ing-1", "rest-1", "Ing 1", "ing-1", null, "u1", "area-1", null, null, null, true, 0L, 0L, null)),
            ingredientUnitOptions = emptyList(),
            suppliers = emptyList(),
            purchaseReceipts = listOf(com.miara.cuentame.core.database.entity.PurchaseReceiptEntity("p-1", "rest-1", null, null, 0L, "POSTED", null, attachmentUri.toString(), 0L, 0L, 0L, null)),
            purchaseLines = emptyList(),
            stockCounts = emptyList(),
            stockCountAreas = emptyList(),
            stockCountLines = emptyList(),
            wasteEvents = emptyList(),
            inventoryMovements = emptyList(),
            inventoryBalanceProjections = emptyList(),
            ingredientCostProjections = emptyList()
        )

        // 1. Create
        val results = repository.createBackup(uri.toString()).toList()
        assertThat(results.last()).isInstanceOf(BackupOperationStatus.Success::class.java)

        // 2. Validate
        val validationResult = repository.validateBackup(uri.toString())
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        
        val valid = validationResult as BackupValidationResult.Valid
        assertThat(valid.manifest.attachments).hasSize(1)
        assertThat(valid.manifest.attachments[0].referencedBy[0].recordId).isEqualTo("p-1")
        
        tempFile.delete()
        attachmentFile.delete()
    }
}
