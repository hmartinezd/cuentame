package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
    fun backup_roundTrip_representativeDataset() = runTest {
        val tempFile = File(context.cacheDir, "roundtrip.zip")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        val uri = Uri.fromFile(tempFile)

        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)
        
        val restId = "rest-1"
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(
            com.miara.cuentame.core.common.ids.RestaurantId(restId), 
            "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        // Create dummy attachment
        val attFile = File(context.cacheDir, "test.jpg")
        attFile.writeBytes(byteArrayOf(1, 2, 3))
        val attUri = Uri.fromFile(attFile)

        val units = listOf(UnitEntity("u1", "Unit", "u", "Mass", BigDecimal.ONE, true, 1))
        val areas = listOf(InventoryAreaEntity("area-1", restId, "Area 1", "area-1", 1, true, 0, 0, null))
        val ingredients = listOf(IngredientEntity("ing-1", restId, "Ing 1", "ing 1", null, "u1", "area-1", null, null, null, true, 0, 0, null))
        val receipts = listOf(PurchaseReceiptEntity("p-1", restId, null, null, 0, "POSTED", null, attUri.toString(), 0, 0, 100, null))

        coEvery { backupDao.createSnapshot(restId) } returns BackupSnapshot(
            restaurants = listOf(RestaurantEntity(restId, "Test Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = areas,
            ingredientCategories = emptyList(),
            units = units,
            ingredients = ingredients,
            ingredientUnitOptions = emptyList(),
            suppliers = emptyList(),
            purchaseReceipts = receipts,
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
        assertThat(valid.manifest.databaseSchemaVersion).isEqualTo(2)
        assertThat(valid.manifest.attachments).hasSize(1)
        
        tempFile.delete()
        attFile.delete()
    }
}
