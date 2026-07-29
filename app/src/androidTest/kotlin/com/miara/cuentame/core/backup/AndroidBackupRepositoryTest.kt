package com.miara.cuentame.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.backup.platform.AndroidBackupDocumentStore
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
import com.miara.cuentame.core.backup.platform.DefaultBackupStorageErrorClassifier
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
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
    fun createBackup_orchestration_smoke_test() = runTest {
        val backupFile = File(tempFolder.root, "smoke.zip")
        val destinationUri = "file://${backupFile.absolutePath}"
        
        val rest = mockk<Restaurant>(relaxed = true) {
            io.mockk.every { id } returns com.miara.cuentame.core.common.ids.RestaurantId("r1")
        }
        coEvery { restaurantRepository.getRestaurant() } returns rest
        
        val snapshotResult = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        coEvery { snapshotSource.loadSnapshot("r1") } returns snapshotResult
        
        val plan = BackupPlan(
            snapshotDto = snapshotResult.dto,
            snapshotJson = "{}".toByteArray(),
            preferencesDto = mockk(relaxed = true) { io.mockk.every { appLocaleTag } returns "en-US" },
            preferencesJson = "{}".toByteArray(),
            attachments = emptyList(),
            manifest = mockk(relaxed = true) {
                io.mockk.every { localeTag } returns "en-US"
                io.mockk.every { attachments } returns emptyList()
            },
            manifestJson = "{\"backupFormatVersion\":1}".toByteArray(),
            expectedEntryChecksums = emptyMap(),
            checksumsJson = "{}".toByteArray(),
            totalUncompressedBytes = 0L
        )
        
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Success(plan)
        
        val results = repository.createBackup(destinationUri).toList()
        
        assertThat(results).isNotEmpty()
        assertThat(results.first()).isEqualTo(BackupOperationStatus.Creating)
    }
}
