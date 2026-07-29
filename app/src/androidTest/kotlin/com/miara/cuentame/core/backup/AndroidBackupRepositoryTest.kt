package com.miara.cuentame.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.backup.platform.*
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshotSource = mockk<BackupSnapshotSource>()
    private val attachmentSource = mockk<BackupAttachmentSource>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val planner = mockk<BackupCreationPlanner>()
    
    private lateinit var documentStore: AndroidBackupDocumentStore
    private lateinit var archiveWriter: DefaultBackupArchiveWriter
    private lateinit var archiveValidator: DefaultBackupArchiveValidator
    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        documentStore = AndroidBackupDocumentStore(context)
        archiveWriter = DefaultBackupArchiveWriter(attachmentSource)
        archiveValidator = DefaultBackupArchiveValidator(BackupJsonCodecs())
        
        repository = AndroidBackupRepository(
            snapshotSource = snapshotSource,
            documentStore = documentStore,
            planner = planner,
            errorClassifier = DefaultBackupStorageErrorClassifier(),
            restaurantRepository = restaurantRepository,
            cleanupCoordinator = BackupCleanupCoordinator(documentStore),
            archiveWriter = archiveWriter,
            archiveValidator = archiveValidator
        )
    }

    @Test
    fun createBackup_successful_sequence() = runTest {
        val backupFile = File(tempFolder.root, "sequence.zip")
        val destinationUri = "file://${backupFile.absolutePath}"
        
        val rest = mockk<Restaurant>(relaxed = true) {
            io.mockk.every { id } returns com.miara.cuentame.core.common.ids.RestaurantId("r1")
        }
        coEvery { restaurantRepository.getRestaurant() } returns rest
        
        val snapshotResult = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        coEvery { snapshotSource.loadSnapshot("r1") } returns snapshotResult
        
        val manifest = mockk<BackupManifest>(relaxed = true)
        
        // Use factory create
        val plan = BackupPlan.create(
            snapshotDto = snapshotResult.dto,
            snapshotJson = "{}".toByteArray(),
            preferencesDto = mockk(relaxed = true),
            preferencesJson = "{}".toByteArray(),
            attachments = emptyList(),
            manifest = manifest,
            manifestJson = "{}".toByteArray(),
            expectedEntryChecksums = mapOf(
                "data/database.json" to "d8e8fca2dc0f896fd7cb4cb0031ba249", // dummy
                "preferences/settings.json" to "d8e8fca2dc0f896fd7cb4cb0031ba249",
                "manifest.json" to "d8e8fca2dc0f896fd7cb4cb0031ba249"
            ),
            checksumsJson = "{}".toByteArray(),
            totalUncompressedBytes = 0L
        )
        
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Success(plan)
        coEvery { archiveWriter.write(any(), any()) } returns BackupArchiveWriteResult.Success
        coEvery { archiveValidator.validate(any()) } returns com.miara.cuentame.core.model.backup.BackupValidationResult.Valid(manifest)

        val results = repository.createBackup(destinationUri).toList()
        
        assertThat(results).containsExactly(
            BackupOperationStatus.Creating,
            BackupOperationStatus.Validating,
            BackupOperationStatus.Success(manifest)
        ).inOrder()
    }
}
