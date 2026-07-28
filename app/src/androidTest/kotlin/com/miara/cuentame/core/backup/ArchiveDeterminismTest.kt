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
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
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
    fun archiveDeterminism_singleMutationProofs() = runTest {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)

        val restId = BackupTestFixtures.RESTAURANT_ID
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(
            RestaurantId(restId), "Test Restaurant", "USD", "en-US", Instant.EPOCH, Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val attFile = File(context.cacheDir, "att_det.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        createdFiles.add(attFile)
        val attUri = Uri.fromFile(attFile)

        val snapshotBase = BackupTestFixtures.createPostedLifecycleSnapshot(
            restaurantId = restId,
            purchaseAttPath = attUri.toString()
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase

        suspend fun generateBackupBytes(file: File): ByteArray {
            val results = repository.createBackup(Uri.fromFile(file).toString()).toList()
            val last = results.last()
            if (last !is BackupOperationStatus.Success) {
                throw AssertionError("Expected Success but got: $last")
            }
            return file.readBytes()
        }

        // Base Proof 1 & 2: Identical inputs produce identical bytes
        val f1 = createTempFile("f1")
        val bytes1 = generateBackupBytes(f1)

        val f2 = createTempFile("f2")
        val bytes2 = generateBackupBytes(f2)
        assertThat(bytes1).isEqualTo(bytes2)

        // Proof 3: Reordered input list produces byte-identical output (sorting)
        val snapshotReordered = snapshotBase.copy(
            inventoryAreas = snapshotBase.inventoryAreas.reversed()
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotReordered
        val fReorder = createTempFile("fReorder")
        val bytesReorder = generateBackupBytes(fReorder)
        assertThat(bytes1).isEqualTo(bytesReorder)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 4: Creation timestamp only
        every { timeProvider.now() } returns Instant.parse("2026-01-02T10:00:00Z")
        val fTime = createTempFile("fTime")
        val bytesTime = generateBackupBytes(fTime)
        assertThat(bytes1).isNotEqualTo(bytesTime)
        every { timeProvider.now() } returns now // restore

        // Proof 5: Preference only
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT.copy(dynamicColorEnabled = false))
        val fPref = createTempFile("fPref")
        val bytesPref = generateBackupBytes(fPref)
        assertThat(bytes1).isNotEqualTo(bytesPref)
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT) // restore

        // Proof 6: Non-manifest database field only (e.g. area updated_at)
        val snapshotChangedDb = snapshotBase.copy(
            inventoryAreas = listOf(
                snapshotBase.inventoryAreas[0].copy(updatedAt = 9999L),
                snapshotBase.inventoryAreas[1]
            )
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotChangedDb
        val fDb = createTempFile("fDb")
        val bytesDb = generateBackupBytes(fDb)
        assertThat(bytes1).isNotEqualTo(bytesDb)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 7: Attachment bytes only
        attFile.writeBytes(byteArrayOf(99, 98, 97, 96))
        val fAttBytes = createTempFile("fAttBytes")
        val bytesAttBytes = generateBackupBytes(fAttBytes)
        assertThat(bytes1).isNotEqualTo(bytesAttBytes)
        attFile.writeBytes(byteArrayOf(1, 2, 3, 4)) // restore

        // Proof 8: Attachment reference graph only (same attachment referenced by waste event as well)
        val snapshotRefGraphMutated = snapshotBase.copy(
            wasteEvents = listOf(
                snapshotBase.wasteEvents[0].copy(attachmentPath = attUri.toString())
            )
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotRefGraphMutated
        val fRefGraph = createTempFile("fRefGraph")
        val bytesRefGraph = generateBackupBytes(fRefGraph)
        assertThat(bytes1).isNotEqualTo(bytesRefGraph)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore

        // Proof 9: Display name only (different filename for attachment source)
        val attFile2 = File(context.cacheDir, "att_different_name.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        createdFiles.add(attFile2)
        val attUri2 = Uri.fromFile(attFile2)
        val snapshotDispNameMutated = snapshotBase.copy(
            purchaseReceipts = listOf(snapshotBase.purchaseReceipts[0].copy(attachmentPath = attUri2.toString()))
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshotDispNameMutated
        val fDispName = createTempFile("fDispName")
        val bytesDispName = generateBackupBytes(fDispName)
        assertThat(bytes1).isNotEqualTo(bytesDispName)
        coEvery { backupDao.createSnapshot(restId) } returns snapshotBase // restore
    }
}
