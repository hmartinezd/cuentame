package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private val backupDao = mockk<BackupDao>()
    private val restaurantRepository = mockk<com.miara.cuentame.core.domain.repository.RestaurantRepository>()
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
    fun backup_roundTrip_validation() = runTest {
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

        coEvery { backupDao.createSnapshot(any()) } returns BackupSnapshot(
            restaurants = listOf(com.miara.cuentame.core.database.entity.RestaurantEntity("rest-1", "Test Rest", "USD", "en", 0L, 0L, null)),
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

        // 1. Create
        val createResult = repository.createBackup(uri.toString())
        assertThat(createResult).isInstanceOf(BackupResult.Success::class.java)

        // 2. Validate
        val validationResult = repository.validateBackup(uri.toString())
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        
        val valid = validationResult as BackupValidationResult.Valid
        assertThat(valid.manifest.applicationId).isEqualTo("com.miara.cuentame")
        assertThat(valid.manifest.createdAtUtc).isEqualTo("2026-01-01T10:00:00Z")
        
        tempFile.delete()
    }
}
