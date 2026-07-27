package com.miara.cuentame.core.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.backup.BackupSnapshot
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
class AndroidBackupRepositoryTest {

    private lateinit var context: Context
    private val contentResolver = mockk<ContentResolver>()
    private val backupDao = mockk<BackupDao>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val checksumProvider = Sha256ChecksumProvider()

    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        context = mockk<Context>()
        every { context.contentResolver } returns contentResolver
        repository = AndroidBackupRepository(
            context,
            backupDao,
            preferencesRepository,
            timeProvider,
            appVersionProvider,
            checksumProvider
        )
    }

    @Test
    fun createBackup_failsWhenDestinationUriCannotBeOpened() = runTest {
        val uriString = "content://com.android.providers.downloads.documents/document/1"
        val uri = Uri.parse(uriString)
        every { contentResolver.openFileDescriptor(uri, "w") } returns null

        val result = repository.createBackup(uriString)

        assertThat(result).isEqualTo(BackupResult.Error.DestinationUnavailable)
    }

    @Test
    fun createBackup_orchestrationSuccess() = runTest {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(realContext.cacheDir, "backup_test.zip")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        
        val uri = Uri.fromFile(tempFile)
        val uriString = uri.toString()
        
        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
        every { contentResolver.openFileDescriptor(any(), "w") } returns pfd
        every { contentResolver.openInputStream(any()) } answers { tempFile.inputStream() }
        every { contentResolver.getType(any()) } returns "application/zip"
        
        val now = Instant.parse("2026-01-01T12:00:00Z")
        every { timeProvider.now() } returns now
        
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 1
        
        coEvery { backupDao.createSnapshot() } returns BackupSnapshot(
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
        
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)

        val result = repository.createBackup(uriString)

        assertThat(result).isInstanceOf(BackupResult.Success::class.java)
        val success = result as BackupResult.Success
        assertThat(success.manifest.createdAtUtc).isEqualTo("2026-01-01T12:00:00Z")
        
        tempFile.delete()
    }
}
