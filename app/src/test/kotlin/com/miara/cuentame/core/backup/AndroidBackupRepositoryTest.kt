package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AndroidBackupRepositoryTest {

    private val snapshotSource = FakeBackupSnapshotSource()
    private val documentStore = FakeBackupDocumentStore()
    private val storageErrorClassifier = FakeBackupStorageErrorClassifier()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val planner = mockk<BackupCreationPlanner>()
    private val cleanupCoordinator = BackupCleanupCoordinator(documentStore)
    private val archiveWriter = mockk<BackupArchiveWriter>()
    private val archiveValidator = mockk<BackupArchiveValidator>()

    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        val gate = com.miara.cuentame.core.backup.internal.RestoreOperationGate()
        gate.updateRecoveryState(com.miara.cuentame.core.backup.api.RestoreStartupState.Ready)
        repository = AndroidBackupRepository(
            snapshotSource = snapshotSource,
            documentStore = documentStore,
            planner = planner,
            errorClassifier = storageErrorClassifier,
            restaurantRepository = restaurantRepository,
            cleanupCoordinator = cleanupCoordinator,
            archiveWriter = archiveWriter,
            archiveValidator = archiveValidator,
            operationGate = gate
        )
    }

    @Test
    fun `full successful orchestration sequence`() = runTest {
        val rest = mockk<Restaurant>(relaxed = true) {
            io.mockk.every { id } returns com.miara.cuentame.core.common.ids.RestaurantId("r1")
        }
        coEvery { restaurantRepository.getRestaurant() } returns rest
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto()
        snapshotSource.result = BackupSnapshotResult(snapshotDto, emptyList())
        
        val manifest = mockk<BackupManifest>()
        val plan = mockk<BackupPlan>(relaxed = true) {
            io.mockk.every { this@mockk.manifest } returns manifest
        }
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Success(plan)
        coEvery { archiveWriter.write(any(), any()) } returns BackupArchiveWriteResult.Success
        coEvery { archiveValidator.validate(any()) } returns com.miara.cuentame.core.model.backup.BackupValidationResult.Valid(manifest)

        val results = repository.createBackup("content://uri").toList()
        
        assertThat(results).containsExactly(
            BackupOperationStatus.Creating,
            BackupOperationStatus.Validating,
            BackupOperationStatus.Success(manifest)
        ).inOrder()
    }

    @Test
    fun `cleans destination on planning failure`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Failure(BackupPlanningFailure.InvalidSnapshot)

        val results = repository.createBackup("content://fail").toList()
        
        assertThat(results.last()).isInstanceOf(BackupOperationStatus.Error::class.java)
        assertThat(documentStore.deleteCalls).isNotEmpty()
    }

    @Test
    fun `backup orchestration rejects AttachmentsNotSupported and never opens stream`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Failure(BackupPlanningFailure.AttachmentsNotSupported)

        val results = repository.createBackup("content://attachments").toList()
        
        val lastResult = results.last() as BackupOperationStatus.Error
        assertThat(lastResult.result).isEqualTo(BackupResult.Error.AttachmentsNotSupported)
        
        assertThat(documentStore.openForWriteCalls).isEmpty()
        io.mockk.coVerify(exactly = 0) { archiveWriter.write(any(), any()) }
    }
}
