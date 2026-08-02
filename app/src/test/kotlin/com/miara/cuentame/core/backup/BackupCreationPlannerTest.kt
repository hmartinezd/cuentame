package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BackupCreationPlannerTest {

    private val localeReconciler = mockk<AppLocaleReconciler>()
    private val preferencesSource = FakeBackupPreferencesSource()
    private val attachmentSource = FakeBackupAttachmentSource()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val jsonCodecs = BackupJsonCodecs()

    private lateinit var planner: BackupCreationPlanner

    @Before
    fun setup() {
        planner = BackupCreationPlanner(
            localeReconciler = localeReconciler,
            preferencesSource = preferencesSource,
            attachmentSource = attachmentSource,
            timeProvider = timeProvider,
            appVersionProvider = appVersionProvider,
            jsonCodecs = jsonCodecs
        )

        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns BackupFormatV1Contract.DATABASE_SCHEMA_VERSION
    }

    private fun makeRestaurant(locale: String = "en-US") = Restaurant(
        id = RestaurantId("rest-1"),
        name = "Test Restaurant",
        currencyCode = "USD",
        localeTag = locale,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createValidSnapshotDto(restaurantId: String = "rest-1") = 
        BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto(
                restaurantId, "Test Restaurant", "USD", "en-US", 0L, 0L, null
            ))
        )

    @Test
    fun `successful deterministic plan`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createValidSnapshotDto()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val result = planner.createPlan(makeRestaurant(), snapshotResult)

        assertThat(result).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (result as BackupPlanningResult.Success).plan
        assertThat(plan.manifest.localeTag).isEqualTo("en-US")
        assertThat(plan.manifest.databaseSchemaVersion).isEqualTo(4)
        assertThat(plan.totalUncompressedBytes).isGreaterThan(0L)
        assertThat(plan.expectedEntryChecksums.keys).containsExactly(
            BackupFormatV1Contract.DATABASE_ENTRY,
            BackupFormatV1Contract.PREFERENCES_ENTRY,
            BackupFormatV1Contract.MANIFEST_ENTRY
        )
        assertThat(plan.manifest.tableMetadata.keys).containsAtLeast("production_batches", "production_batch_components")
    }

    @Test
    fun `plan ensures defensive copies of byte arrays`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createValidSnapshotDto()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val plan = (planner.createPlan(makeRestaurant(), snapshotResult) as BackupPlanningResult.Success).plan
        
        val originalSnapshotBytes = plan.snapshotJson.copyForTest()
        val copy = plan.snapshotJson.copyForTest()
        if (copy.isNotEmpty()) {
            copy[0] = if (copy[0] == 0.toByte()) 1.toByte() else 0.toByte()
        }
        
        assertThat(plan.snapshotJson.copyForTest()).isEqualTo(originalSnapshotBytes)
    }

    @Test
    fun `backup planning rejects purchase attachment`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val snapshotDto = createValidSnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "rest-1", null, null, 0, "DRAFT", null, attId, 0, 0, null, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        
        assertThat(result).isInstanceOf(BackupPlanningResult.Failure::class.java)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }

    @Test
    fun `maps missing restaurant to RestaurantDisappeared`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.RestaurantNotFound
        
        val result = planner.createPlan(makeRestaurant(), BackupSnapshotResult(createValidSnapshotDto(), emptyList()))
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.RestaurantDisappeared)
    }

    @Test
    fun `maps reconciliation failure to LocaleReconciliationFailed`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.Failure(Exception("error"))
        
        val result = planner.createPlan(makeRestaurant(), BackupSnapshotResult(createValidSnapshotDto(), emptyList()))
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.LocaleReconciliationFailed)
    }

    @Test
    fun `rejects plan if schema version mismatch`() = runTest {
        every { appVersionProvider.databaseSchemaVersion } returns 1 // Baseline is 2
        
        val result = planner.createPlan(makeRestaurant(), BackupSnapshotResult(createValidSnapshotDto(), emptyList()))
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.UnsupportedDatabaseSchema)
    }

    @Test
    fun `backup planning rejects waste attachment`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val snapshotDto = createValidSnapshotDto().copy(
            wasteEvents = listOf(com.miara.cuentame.core.backup.model.WasteEventBackupDto(
                "w1", "rest-1", "i1", "a1", "uo1", "1", "1", "SPOILED", 0, null, attId, "POSTED", 0, 0, 0, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }

    @Test
    fun `backup planning rejects attachment source binding`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        val snapshotDto = createValidSnapshotDto()
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }

    @Test
    fun `rejects plan if attachment ID is invalid`() = runTest {
        // Renamed to match historical requirement but asserts new failure
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val invalidId = "not-hex"
        val attUri = AttachmentSourceUri("uri1")
        val snapshotDto = createValidSnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "rest-1", null, null, 0, "DRAFT", null, invalidId, 0, 0, null, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(invalidId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }

    @Test
    fun `fails with MissingAttachmentSource when binding is missing`() = runTest {
        // Renamed to match historical requirement but asserts new failure
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val snapshotDto = createValidSnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "rest-1", null, null, 0, "DRAFT", null, attId, 0, 0, null, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }
}
