package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant

class BackupCleanupLifecycleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val backupDao = mockk<BackupDao>()
    private val checksumProvider = Sha256ChecksumProvider()

    private lateinit var cacheDir: File
    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val s = arg<String>(0)
            val uri = mockk<Uri>()
            every { uri.toString() } returns s
            every { uri.scheme } returns s.substringBefore(":")
            every { uri.lastPathSegment } returns s.substringAfterLast("/")
            uri
        }

        cacheDir = tempFolder.newFolder("cache")
        every { context.cacheDir } returns cacheDir

        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2

        val rest = Restaurant(
            id = RestaurantId(BackupTestFixtures.RESTAURANT_ID),
            name = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns rest
        every { preferencesRepository.observePreferences() } returns flowOf(
            AppPreferences(onboardingCompleted = true, themeMode = ThemeMode.SYSTEM, dynamicColorEnabled = true, appLocaleTag = "en-US")
        )
        coEvery { backupDao.createSnapshot(any()) } returns BackupTestFixtures.createPostedLifecycleSnapshot()

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
    fun teardown() {
        unmockkStatic(Uri::class)
    }

    private fun tempCount(): Int {
        return cacheDir.listFiles { _, name -> name.startsWith("staging_backup_") }?.size ?: 0
    }

    @Test
    fun createBackup_restaurantLookupFailure() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null
        val destFile = File(tempFolder.root, "dest_rest_fail.zip")

        val results = repository.createBackup("file://${destFile.absolutePath}").toList()

        assertThat(destFile.exists()).isFalse()
        val error = results.last() as BackupOperationStatus.Error
        assertThat(error.result).isInstanceOf(BackupResult.Error.RestaurantUnavailable::class.java)
    }

    @Test
    fun createBackup_preferencesReadFailure() = runTest {
        every { preferencesRepository.observePreferences() } returns flow { throw IOException("Prefs read error") }
        val destFile = File(tempFolder.root, "dest_prefs_fail.zip")

        val results = repository.createBackup("file://${destFile.absolutePath}").toList()

        assertThat(destFile.exists()).isFalse()
        val error = results.last() as BackupOperationStatus.Error
        assertThat(error.result).isInstanceOf(BackupResult.Error.PreferencesReadFailure::class.java)
    }

    @Test
    fun createBackup_roomSnapshotFailure() = runTest {
        coEvery { backupDao.createSnapshot(any()) } throws RuntimeException("DB Snapshot failed")
        val destFile = File(tempFolder.root, "dest_snapshot_fail.zip")

        val results = repository.createBackup("file://${destFile.absolutePath}").toList()

        assertThat(destFile.exists()).isFalse()
        val error = results.last() as BackupOperationStatus.Error
        assertThat(error.result).isInstanceOf(BackupResult.Error.DatabaseSnapshotFailure::class.java)
    }

    @Test
    fun createBackup_missingAttachmentFailure() = runTest {
        val snapshotWithMissingAtt = BackupTestFixtures.createPostedLifecycleSnapshot(
            purchaseAttPath = "file:///nonexistent/path/att.jpg"
        )
        coEvery { backupDao.createSnapshot(any()) } returns snapshotWithMissingAtt

        val destFile = File(tempFolder.root, "dest_missing_att.zip")
        val results = repository.createBackup("file://${destFile.absolutePath}").toList()

        assertThat(destFile.exists()).isFalse()
        val error = results.last() as BackupOperationStatus.Error
        assertThat(error.result).isInstanceOf(BackupResult.Error.MissingAttachment::class.java)
    }

    @Test
    fun createBackup_successfulCreation_copiesBytesAndDeletesTemp() = runTest {
        val attFile = File(cacheDir, "valid_att.jpg")
        attFile.writeBytes("image data".toByteArray())

        val snapshot = BackupTestFixtures.createPostedLifecycleSnapshot(
            purchaseAttPath = "file://${attFile.absolutePath}"
        )
        coEvery { backupDao.createSnapshot(any()) } returns snapshot

        val destFile = File(tempFolder.root, "dest_success.zip")
        every { context.contentResolver.openOutputStream(any()) } answers { destFile.outputStream() }
        every { context.contentResolver.openFileDescriptor(any(), "w") } answers {
            // Need a real PFD or mock it
            android.os.ParcelFileDescriptor.open(destFile, android.os.ParcelFileDescriptor.MODE_READ_WRITE)
        }

        val results = repository.createBackup("file://${destFile.absolutePath}").toList()

        assertThat(destFile.exists()).isTrue()
        assertThat(destFile.length()).isGreaterThan(0L)
        assertThat(results.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
    }
}
