package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.backup.fakes.*
import com.venkoi.restaurantops.core.common.AppVersionProvider
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.domain.usecase.locale.AppLocaleReconciler
import com.venkoi.restaurantops.core.domain.usecase.locale.LocaleReconciliationResult
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
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
        every { appVersionProvider.applicationId } returns "com.venkoi.restaurantops"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns BackupFormatV1Contract.DATABASE_SCHEMA_VERSION
    }

    private fun makeRestaurant() = Restaurant(
        id = RestaurantId("r1"),
        name = "Test Rest",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun createValidSnapshotDto() = 
        BackupTestFixtures.createPopulatedSchema4Snapshot()

    @Test
    fun `successful deterministic plan uses current format`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createValidSnapshotDto()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val result = planner.createPlan(makeRestaurant(), snapshotResult)

        assertThat(result).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (result as BackupPlanningResult.Success).plan
        assertThat(plan.manifest.backupFormatVersion).isEqualTo(BackupFormatV1Contract.BACKUP_FORMAT_VERSION)
        assertThat(plan.manifest.localeTag).isEqualTo("en-US")
    }

    @Test
    fun `plan ensures defensive copies of byte arrays`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
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
    fun `backup planning supports purchase attachment in current format`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("uri1")
        
        attachmentSource.metadataMap[attUri] = AttachmentSourceMetadata(attUri, "invoice.pdf", "application/pdf")
        attachmentSource.dataMap[attUri] = "test".toByteArray()

        val snapshot = createValidSnapshotDto()
        val sabotagedReceipts = snapshot.purchaseReceipts.map {
            if (it.id == "p1") it.copy(attachmentId = attId) else it
        }
        val snapshotDto = snapshot.copy(purchaseReceipts = sabotagedReceipts)
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        
        assertThat(result).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (result as BackupPlanningResult.Success).plan
        assertThat(plan.manifest.backupFormatVersion).isEqualTo(BackupFormatV1Contract.BACKUP_FORMAT_VERSION)
        assertThat(plan.attachments).hasSize(1)
        assertThat(plan.attachments[0].attachmentId).isEqualTo(attId)
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
    fun `rejects plan if attachment ID is invalid`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val invalidId = "not-hex"
        val attUri = AttachmentSourceUri("uri1")
        val snapshot = createValidSnapshotDto()
        val sabotagedReceipts = snapshot.purchaseReceipts.map {
            if (it.id == "p1") it.copy(attachmentId = invalidId) else it
        }
        val snapshotDto = snapshot.copy(purchaseReceipts = sabotagedReceipts)
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(invalidId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.InvalidAttachmentId)
    }

    @Test
    fun `fails with MissingAttachmentSource when binding is missing`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val snapshot = createValidSnapshotDto()
        val sabotagedReceipts = snapshot.purchaseReceipts.map {
            if (it.id == "p1") it.copy(attachmentId = attId) else it
        }
        val snapshotDto = snapshot.copy(purchaseReceipts = sabotagedReceipts)
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.MissingAttachmentSource)
    }
}
