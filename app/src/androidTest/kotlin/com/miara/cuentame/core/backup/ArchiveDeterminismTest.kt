package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupSnapshot
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
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
    private val createdFiles = mutableListOf<File>()

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

    @After
    fun tearDown() {
        createdFiles.forEach { if (it.exists()) it.delete() }
    }

    private fun createTempFile(prefix: String): File {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}_${(0..999).random()}.zip")
        if (file.exists()) file.delete()
        file.createNewFile()
        createdFiles.add(file)
        return file
    }

    @Test
    fun archiveDeterminism_comprehensiveProofs() = runTest {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2 // Schema version 2
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)

        val restId = "rest-1"
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(RestaurantId(restId), "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val attFile = File(context.cacheDir, "att_det.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        createdFiles.add(attFile)
        val attUri = Uri.fromFile(attFile)

        val area1 = InventoryAreaEntity("area-1", restId, "Area 1", "area-1", 1, true, 0, 0, null)
        val area2 = InventoryAreaEntity("area-2", restId, "Area 2", "area-2", 2, true, 0, 0, null)
        val unit1 = UnitEntity("u-1", "Unit", "u", "Mass", BigDecimal.ONE, true, 1)
        val receipt1 = PurchaseReceiptEntity("pr-1", restId, null, "INV-1", 1000, "POSTED", null, attUri.toString(), 0, 0, 100, null)

        val snapshotBase = BackupSnapshot(
            restaurants = listOf(RestaurantEntity(restId, "Test Rest", "USD", "en-US", 0L, 100L, null)),
            inventoryAreas = listOf(area1, area2),
            ingredientCategories = emptyList(),
            units = listOf(unit1),
            ingredients = emptyList(),
            ingredientUnitOptions = emptyList(),
            suppliers = emptyList(),
            purchaseReceipts = listOf(receipt1),
            purchaseLines = emptyList(),
            stockCounts = emptyList(),
            stockCountAreas = emptyList(),
            stockCountLines = emptyList(),
            wasteEvents = emptyList(),
            inventoryMovements = emptyList(),
            inventoryBalanceProjections = emptyList(),
            ingredientCostProjections = emptyList()
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase

        suspend fun generateBackupBytes(file: File): ByteArray {
            val results = repository.createBackup(Uri.fromFile(file).toString()).toList()
            assertThat(results.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
            return file.readBytes()
        }

        // Proof 1: Identical logical inputs produce identical bytes
        val f1 = createTempFile("f1")
        val bytes1 = generateBackupBytes(f1)

        val f2 = createTempFile("f2")
        val bytes2 = generateBackupBytes(f2)
        assertThat(bytes1).isEqualTo(bytes2)

        // Proof 2: Different source list ordering produces identical bytes (explicit DTO sorting)
        val snapshotReordered = snapshotBase.copy(
            inventoryAreas = listOf(area2, area1) // reversed list order
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotReordered
        val fReorder = createTempFile("fReorder")
        val bytesReorder = generateBackupBytes(fReorder)
        assertThat(bytes1).isEqualTo(bytesReorder)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 3: Changing timestamp changes bytes
        every { timeProvider.now() } returns Instant.parse("2026-01-02T10:00:00Z")
        val fTime = createTempFile("fTime")
        val bytesTime = generateBackupBytes(fTime)
        assertThat(bytes1).isNotEqualTo(bytesTime)
        every { timeProvider.now() } returns now // restore

        // Proof 4: Changing preference changes bytes
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT.copy(dynamicColorEnabled = false))
        val fPref = createTempFile("fPref")
        val bytesPref = generateBackupBytes(fPref)
        assertThat(bytes1).isNotEqualTo(bytesPref)
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT) // restore

        // Proof 5: Changing database entity value changes bytes
        val snapshotChangedDb = snapshotBase.copy(
            restaurants = listOf(RestaurantEntity(restId, "Test Rest", "USD", "en-US", 0L, 999L, null))
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotChangedDb
        val fDb = createTempFile("fDb")
        val bytesDb = generateBackupBytes(fDb)
        assertThat(bytes1).isNotEqualTo(bytesDb)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 6: Changing attachment bytes changes bytes
        attFile.writeBytes(byteArrayOf(99, 98, 97, 96)) // modified bytes
        val fAttBytes = createTempFile("fAttBytes")
        val bytesAttBytes = generateBackupBytes(fAttBytes)
        assertThat(bytes1).isNotEqualTo(bytesAttBytes)
        attFile.writeBytes(byteArrayOf(1, 2, 3, 4)) // restore

        // Proof 7: Changing attachment filename changes bytes
        val renamedAttFile = File(context.cacheDir, "att_renamed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        createdFiles.add(renamedAttFile)
        val snapshotRenamedAtt = snapshotBase.copy(
            purchaseReceipts = listOf(receipt1.copy(attachmentPath = Uri.fromFile(renamedAttFile).toString()))
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotRenamedAtt
        val fAttName = createTempFile("fAttName")
        val bytesAttName = generateBackupBytes(fAttName)
        assertThat(bytes1).isNotEqualTo(bytesAttName)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 8: Changing attachment references changes bytes
        val snapshotRefChange = snapshotBase.copy(
            purchaseReceipts = listOf(receipt1.copy(id = "pr-changed-id"))
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotRefChange
        val fRefChange = createTempFile("fRefChange")
        val bytesRefChange = generateBackupBytes(fRefChange)
        assertThat(bytes1).isNotEqualTo(bytesRefChange)
    }
}
