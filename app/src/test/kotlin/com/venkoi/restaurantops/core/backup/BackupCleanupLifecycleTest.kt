package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.backup.fakes.*
import com.venkoi.restaurantops.core.backup.internal.BackupCleanupCoordinator
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.BackupOperationStatus
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.backup.BackupResult
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BackupCleanupLifecycleTest {

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
        val gate = com.venkoi.restaurantops.core.backup.internal.RestoreOperationGate()
        gate.updateRecoveryState(com.venkoi.restaurantops.core.backup.api.RestoreStartupState.Ready)
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

        val rest = Restaurant(
            id = RestaurantId("rest-1"),
            name = "Test Restaurant",
            currencyCode = "USD",
            localeTag = "en-US",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns rest
    }

    @Test
    fun `preflight failure does not open destination for write`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null
        
        val results = repository.createBackup("content://backup.zip").toList()

        assertThat(documentStore.openForWriteCalls).isEmpty()
        val error = results.last() as BackupOperationStatus.Error
        assertThat(error.result).isInstanceOf(BackupResult.Error.RestaurantUnavailable::class.java)
    }

    @Test
    fun `creation failure triggers cleanup`() = runTest {
        val rest = Restaurant(RestaurantId("r1"), "R", "USD", "en-US", Instant.now(), Instant.now())
        coEvery { restaurantRepository.getRestaurant() } returns rest
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Success(
            mockk(relaxed = true) {
                io.mockk.every { snapshotJson } returns ImmutableBackupBytes.from(ByteArray(0))
                io.mockk.every { preferencesJson } returns ImmutableBackupBytes.from(ByteArray(0))
            }
        )
        
        // Make writer throw
        coEvery { archiveWriter.write(any(), any()) } throws java.io.IOException("Disk full")

        val docUri = "content://fail.zip"
        repository.createBackup(docUri).toList()

        assertThat(documentStore.deleteCalls).contains(BackupDocumentUri(docUri))
    }

    @Test
    fun `failed deletion falls back to truncate`() = runTest {
        val rest = Restaurant(RestaurantId("r1"), "R", "USD", "en-US", Instant.now(), Instant.now())
        coEvery { restaurantRepository.getRestaurant() } returns rest
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyList())
        coEvery { planner.createPlan(any(), any()) } returns BackupPlanningResult.Success(
            mockk(relaxed = true) {
                io.mockk.every { snapshotJson } returns ImmutableBackupBytes.from(ByteArray(0))
                io.mockk.every { preferencesJson } returns ImmutableBackupBytes.from(ByteArray(0))
            }
        )

        documentStore.deleteResult = false
        documentStore.truncateResult = true
        
        // Force failure after open
        coEvery { archiveWriter.write(any(), any()) } throws java.io.IOException("Fail")

        val docUri = "content://truncate.zip"
        repository.createBackup(docUri).toList()

        assertThat(documentStore.deleteCalls).contains(BackupDocumentUri(docUri))
        assertThat(documentStore.truncateCalls).contains(BackupDocumentUri(docUri))
    }
}
