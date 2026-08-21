package com.venkoi.restaurantops.core.backup.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.AndroidBackupRepository
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.domain.repository.BackupOperationStatus
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import com.venkoi.restaurantops.core.preferences.model.AppPreferences

class AutoBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val backupRepository = mockk<AndroidBackupRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>(relaxed = true)
    private val timeProvider = mockk<TimeProvider>()

    private lateinit var repository: AutoBackupRepository
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        every { timeProvider.now() } returns Instant.parse("2026-08-12T21:30:00Z")
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)
        
        repository = AutoBackupRepository(
            context = context,
            backupRepository = backupRepository,
            preferencesRepository = preferencesRepository,
            timeProvider = timeProvider
        )
    }

    @Test
    fun `performAutoBackup creates file and updates status on success`() = runBlocking {
        val manifest = mockk<BackupManifest>()
        every { backupRepository.createBackup(any()) } answers {
            val path = firstArg<String>()
            File(path).createNewFile()
            flowOf(BackupOperationStatus.Success(manifest))
        }

        repository.performAutoBackup()

        val backupDir = File(filesDir, "backups")
        assertThat(backupDir.exists()).isTrue()
        val backups = backupDir.listFiles { f -> f.extension == "zip" }
        assertThat(backups).hasLength(1)
        assertThat(backups!![0].name).isEqualTo("restaurantops-auto-2026-08-12T213000Z.zip")

        coVerify { 
            preferencesRepository.updateAutoBackupStatus(
                successTimestamp = any(),
                attemptTimestamp = any(),
                result = "SUCCESS"
            )
        }
    }

    @Test
    fun `rotateBackups keeps only 7 most recent backups`() = runBlocking {
        val backupDir = File(filesDir, "backups")
        backupDir.mkdirs()
        
        // Create 10 old backups
        for (i in 1..10) {
            val day = i.toString().padStart(2, '0')
            File(backupDir, "restaurantops-auto-2026-08-${day}T000000Z.zip").createNewFile()
        }

        val manifest = mockk<BackupManifest>()
        every { backupRepository.createBackup(any()) } answers {
            val path = firstArg<String>()
            File(path).createNewFile()
            flowOf(BackupOperationStatus.Success(manifest))
        }

        repository.performAutoBackup()

        val backups = backupDir.listFiles { f -> f.extension == "zip" }?.sortedBy { it.name }
        assertThat(backups).hasSize(7)
        // Verify that the oldest ones were deleted (1-5 deleted, 6-10 remain + new one)
        // Wait, 11 backups total (10 old + 1 new), keep 7 newest.
        // Old: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10
        // New: 12
        // Sorted desc: 12, 10, 09, 08, 07, 06, 05
        // 04, 03, 02, 01 deleted.
        assertThat(backups!![0].name).isEqualTo("restaurantops-auto-2026-08-05T000000Z.zip")
        assertThat(backups.last().name).isEqualTo("restaurantops-auto-2026-08-12T213000Z.zip")
    }

    @Test
    fun `failed backup does not delete previous backups`() = runBlocking {
        val backupDir = File(filesDir, "backups")
        backupDir.mkdirs()
        val oldBackup = File(backupDir, "restaurantops-auto-2026-08-11T000000Z.zip")
        oldBackup.createNewFile()

        every { backupRepository.createBackup(any()) } returns flowOf(BackupOperationStatus.Error(mockk(relaxed = true)))

        repository.performAutoBackup()

        assertThat(oldBackup.exists()).isTrue()
        val backups = backupDir.listFiles { f -> f.extension == "zip" }
        assertThat(backups).hasLength(1)
    }
}
