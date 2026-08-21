package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.backup.fakes.*
import com.venkoi.restaurantops.core.backup.platform.DefaultBackupArchiveValidator
import com.venkoi.restaurantops.core.backup.platform.DefaultBackupArchiveWriter
import com.venkoi.restaurantops.core.common.AppVersionProvider
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.domain.usecase.locale.AppLocaleReconciler
import com.venkoi.restaurantops.core.domain.usecase.locale.LocaleReconciliationResult
import com.venkoi.restaurantops.core.model.backup.BackupValidationResult
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class BackupRoundTripTest {

    private val localeReconciler = mockk<AppLocaleReconciler>()
    private val preferencesSource = FakeBackupPreferencesSource()
    private val attachmentSource = FakeBackupAttachmentSource()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val jsonCodecs = BackupJsonCodecs()

    private lateinit var planner: BackupCreationPlanner
    private lateinit var writer: DefaultBackupArchiveWriter
    private lateinit var validator: DefaultBackupArchiveValidator
    private lateinit var reader: BackupArchiveReader

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
        writer = DefaultBackupArchiveWriter(attachmentSource)
        validator = DefaultBackupArchiveValidator(jsonCodecs)
        val processor = com.venkoi.restaurantops.core.backup.internal.BackupArchiveProcessor(BackupReadLimits()) { input -> java.util.zip.ZipInputStream(input) }
        val fingerprinter = com.venkoi.restaurantops.core.backup.internal.BackupArchiveFingerprinter(jsonCodecs)
        reader = com.venkoi.restaurantops.core.backup.platform.DefaultBackupArchiveReader(jsonCodecs, processor, fingerprinter)

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

    @Test
    fun `complete round trip with no attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createPopulatedSnapshot()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val planResult = planner.createPlan(makeRestaurant(), snapshotResult)
        if (planResult is BackupPlanningResult.Failure) throw Exception("Planning failed: ${planResult.reason}")
        
        val plan = (planResult as BackupPlanningResult.Success).plan
        val output = ByteArrayOutputStream()
        val result = writer.write(output, plan)
        assertThat(result).isEqualTo(BackupArchiveWriteResult.Success)

        val validation = validator.validate(ByteArrayInputStream(output.toByteArray()))
        assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)

        val inspection = reader.inspect(ByteArrayInputStream(output.toByteArray()), BackupDocumentUri("uri"))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        
        assertThat(ready.archive.snapshot).isEqualTo(snapshotDto)
        assertThat(ready.preview.restaurantName).isEqualTo("Test Rest")
    }

    private fun createPopulatedSnapshot() = BackupTestFixtures.createPopulatedCurrentSnapshot()

    @Test
    fun `complete round trip without attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createPopulatedSnapshot()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val planResult = planner.createPlan(makeRestaurant(), snapshotResult)
        if (planResult is BackupPlanningResult.Failure) throw Exception("Planning failed: ${planResult.reason}")
        
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        val output = ByteArrayOutputStream()
        val writeResult = writer.write(output, plan)
        assertThat(writeResult).isEqualTo(BackupArchiveWriteResult.Success)
        
        val validation = validator.validate(ByteArrayInputStream(output.toByteArray()))
        if (validation is BackupValidationResult.Invalid) throw Exception("Validation failed: ${validation.code} ${validation.diagnostic}")
        assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)

        val inspection = reader.inspect(ByteArrayInputStream(output.toByteArray()), BackupDocumentUri("uri"))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        
        assertThat(ready.archive.attachmentSummaries).isEmpty()
        
        // Verify planned vs inspected snapshot equality
        assertThat(ready.archive.snapshot).isEqualTo(snapshotDto)
        assertThat(ready.archive.preferences).isEqualTo(preferencesSource.result)
        assertThat(ready.archive.manifest).isEqualTo(plan.manifest)
    }

    @Test
    fun `archive determinism - identical inputs produce identical bytes`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createPopulatedSnapshot()
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val plan1 = (planner.createPlan(makeRestaurant(), snapshotResult) as BackupPlanningResult.Success).plan
        val plan2 = (planner.createPlan(makeRestaurant(), snapshotResult) as BackupPlanningResult.Success).plan

        val out1 = ByteArrayOutputStream()
        val out2 = ByteArrayOutputStream()
        
        val res1 = writer.write(out1, plan1)
        val res2 = writer.write(out2, plan2)

        assertThat(res1).isEqualTo(BackupArchiveWriteResult.Success)
        assertThat(res2).isEqualTo(BackupArchiveWriteResult.Success)
        assertThat(out1.toByteArray()).isEqualTo(out2.toByteArray())
    }

    @Test
    fun `complete round trip with shared attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("content://shared")
        
        attachmentSource.metadataMap[attUri] = AttachmentSourceMetadata(attUri, "photo.jpg", "image/jpeg")
        attachmentSource.dataMap[attUri] = "test-bytes".toByteArray()

        val snapshot = createPopulatedSnapshot()
        val sabotagedReceipts = snapshot.purchaseReceipts.map {
            if (it.id == "p1") it.copy(attachmentId = attId) else it
        }
        val snapshotDto = snapshot.copy(purchaseReceipts = sabotagedReceipts)
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val planResult = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        
        val plan = (planResult as BackupPlanningResult.Success).plan
        val output = ByteArrayOutputStream()
        writer.write(output, plan)

        val inspection = reader.inspect(ByteArrayInputStream(output.toByteArray()), BackupDocumentUri("uri"))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        
        assertThat(ready.archive.attachmentSummaries).hasSize(1)
        assertThat(ready.archive.attachmentSummaries[0].attachmentId).isEqualTo(attId)
    }
}
