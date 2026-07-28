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
import com.miara.cuentame.core.model.backup.BackupSnapshot
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
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ArchiveDeterminismTest {

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
    fun archivesAreBitForByteIdenticalForSameInputs() = runTest {
        val file1 = File(context.cacheDir, "backup1.zip")
        val file2 = File(context.cacheDir, "backup2.zip")
        val file3 = File(context.cacheDir, "backup3.zip")
        val file4 = File(context.cacheDir, "backup4.zip")
        listOf(file1, file2, file3, file4).forEach { if (it.exists()) it.delete(); it.createNewFile() }

        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 1
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)

        val restId = com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(restId, "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val snapshot = BackupSnapshot(
            restaurants = listOf(com.miara.cuentame.core.database.entity.RestaurantEntity("rest-1", "Test Rest", "USD", "en-US", 0L, 0L, null)),
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
        coEvery { backupDao.createSnapshot("rest-1") } returns snapshot

        // 1. Create first backup
        val results1 = repository.createBackup(Uri.fromFile(file1).toString()).toList()
        assertThat(results1.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
        val bytes1 = file1.readBytes()

        // 2. Create second backup (identical inputs)
        val results2 = repository.createBackup(Uri.fromFile(file2).toString()).toList()
        assertThat(results2.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
        val bytes2 = file2.readBytes()

        assertThat(bytes1).isEqualTo(bytes2)

        // 3. Change a database value
        coEvery { backupDao.createSnapshot("rest-1") } returns snapshot.copy(
            restaurants = listOf(com.miara.cuentame.core.database.entity.RestaurantEntity("rest-1", "CHANGED", "USD", "en-US", 0L, 0L, null))
        )
        repository.createBackup(Uri.fromFile(file3).toString()).toList()
        val bytes3 = file3.readBytes()
        assertThat(bytes1).isNotEqualTo(bytes3)
        
        // 4. Change a preference
        coEvery { backupDao.createSnapshot("rest-1") } returns snapshot
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT.copy(appLocaleTag = "es-US"))
        repository.createBackup(Uri.fromFile(file4).toString()).toList()
        val bytes4 = file4.readBytes()
        assertThat(bytes1).isNotEqualTo(bytes4)

        listOf(file1, file2, file3, file4).forEach { it.delete() }
    }
}
