package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupSnapshotResult
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BackupCleanupLifecycleTest {

    private val snapshotSource = FakeBackupSnapshotSource()
    private val preferencesSource = FakeBackupPreferencesSource()
    private val attachmentSource = FakeBackupAttachmentSource()
    private val documentStore = FakeBackupDocumentStore()
    private val storageErrorClassifier = FakeBackupStorageErrorClassifier()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val planner = mockk<BackupCreationPlanner>()
    private val cleanupCoordinator = BackupCleanupCoordinator(documentStore)

    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        repository = AndroidBackupRepository(
            snapshotSource = snapshotSource,
            preferencesSource = preferencesSource,
            attachmentSource = attachmentSource,
            documentStore = documentStore,
            planner = planner,
            errorClassifier = storageErrorClassifier,
            restaurantRepository = restaurantRepository,
            cleanupCoordinator = cleanupCoordinator
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
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyMap())
        preferencesSource.result = BackupPreferencesDto("SYSTEM", true, "en-US")
        
        // Planner succeeds but ZIP creation will fail because we'll throw in performBackup (simulated by closing store)
        coEvery { planner.createPlan(any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        
        // Make openForWrite throw
        documentStore.writeStream = mockk()
        io.mockk.every { documentStore.writeStream.write(any<ByteArray>()) } throws java.io.IOException("Disk full")

        val docUri = "content://fail.zip"
        repository.createBackup(docUri).toList()

        assertThat(documentStore.deleteCalls).contains(BackupDocumentUri(docUri))
        assertThat(cleanupCoordinator.lastCleanupOutcome).isEqualTo(BackupCleanupCoordinator.CleanupOutcome.Deleted)
    }

    @Test
    fun `failed deletion falls back to truncate`() = runTest {
        val rest = Restaurant(RestaurantId("r1"), "R", "USD", "en-US", Instant.now(), Instant.now())
        coEvery { restaurantRepository.getRestaurant() } returns rest
        snapshotSource.result = BackupSnapshotResult(BackupTestFixtures.createEmptySnapshotDto(), emptyMap())
        preferencesSource.result = BackupPreferencesDto("SYSTEM", true, "en-US")
        coEvery { planner.createPlan(any(), any(), any()) } returns Result.success(mockk(relaxed = true))

        documentStore.deleteResult = false
        documentStore.truncateResult = true
        
        // Force failure after open
        documentStore.writeStream = mockk()
        io.mockk.every { documentStore.writeStream.write(any<Int>()) } throws java.io.IOException("Fail")

        val docUri = "content://truncate.zip"
        repository.createBackup(docUri).toList()

        assertThat(documentStore.deleteCalls).contains(BackupDocumentUri(docUri))
        assertThat(documentStore.truncateCalls).contains(BackupDocumentUri(docUri))
        assertThat(cleanupCoordinator.lastCleanupOutcome).isEqualTo(BackupCleanupCoordinator.CleanupOutcome.Truncated)
    }
}
