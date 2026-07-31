package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveWriter
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
        val processor = com.miara.cuentame.core.backup.internal.BackupArchiveProcessor(BackupReadLimits()) { input -> java.util.zip.ZipInputStream(input) }
        val fingerprinter = com.miara.cuentame.core.backup.internal.BackupArchiveFingerprinter(jsonCodecs)
        reader = com.miara.cuentame.core.backup.platform.DefaultBackupArchiveReader(jsonCodecs, processor, fingerprinter)

        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns BackupFormatV1Contract.DATABASE_SCHEMA_VERSION
    }

    private fun makeRestaurant() = Restaurant(
        id = RestaurantId("rest-1"),
        name = "Test Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Test
    fun `complete round trip with no attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto(
                "rest-1", "Test Restaurant", "USD", "en-US", 0L, 0L, null
            ))
        )
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
        assertThat(ready.preview.restaurantName).isEqualTo("Test Restaurant")
    }

    @Test
    fun `complete round trip without attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto("rest-1", "Test Restaurant", "USD", "en-US", 0L, 0L, null)),
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto("p1", "rest-1", null, null, 0, "POSTED", null, null, 1000, 1000, 1000, null)),
            wasteEvents = listOf(com.miara.cuentame.core.backup.model.WasteEventBackupDto("w1", "rest-1", "i1", "a1", "o1", "1.0", "1.0", "SPOILED", 1000, null, null, "POSTED", 1000, 1000, 1000, null)),
            ingredients = listOf(com.miara.cuentame.core.backup.model.IngredientBackupDto("i1", "rest-1", "I", "i", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            inventoryAreas = listOf(com.miara.cuentame.core.backup.model.InventoryAreaBackupDto("a1", "rest-1", "A", "a", 0, true, 0, 0, null)),
            units = listOf(com.miara.cuentame.core.backup.model.UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 0)),
            ingredientUnitOptions = listOf(com.miara.cuentame.core.backup.model.IngredientUnitOptionBackupDto("o1", "i1", "O", "o", null, "1.0", true, true, true, true, 0, 0, null)),
            inventoryMovements = listOf(
                com.miara.cuentame.core.backup.model.InventoryMovementBackupDto("m1", "rest-1", "i1", "a1", "PURCHASE", "1.0", "1.0", "1.0", 1000L, "PURCHASE_RECEIPT", "p1", "op1", "p1-l1", null, 1000L),
                com.miara.cuentame.core.backup.model.InventoryMovementBackupDto("m2", "rest-1", "i1", "a1", "WASTE", "-1.0", "1.0", "-1.0", 1000L, "WASTE_EVENT", "w1", "op2", "w1", null, 1000L)
            ),
            purchaseLines = listOf(com.miara.cuentame.core.backup.model.PurchaseLineBackupDto("p1-l1", "p1", "i1", "a1", "o1", "1.0", "1.0", "1.0", "1.0", null, 0, 0)),
            inventoryBalanceProjections = listOf(com.miara.cuentame.core.backup.model.InventoryBalanceProjectionBackupDto("rest-1", "i1", "a1", "0.0", 0L)),
            ingredientCostProjections = listOf(com.miara.cuentame.core.backup.model.IngredientCostProjectionBackupDto("rest-1", "i1", "1.0", 0L))
        )
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
        
        // Payload-retention boundary: InspectedBackupArchive should not have ByteArray or InputStream fields
        val fields = ready.archive::class.java.declaredFields
        for (field in fields) {
            assertThat(field.type).isNotEqualTo(ByteArray::class.java)
            assertThat(InputStream::class.java.isAssignableFrom(field.type)).isFalse()
        }
    }

    @Test
    fun `archive determinism - identical inputs produce identical bytes`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto(
                "rest-1", "Test Restaurant", "USD", "en-US", 0L, 0L, null
            ))
        )
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
        // Historical test name preserved. Authoritative V1 behavior: Rejection.
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("content://shared")
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "p1", "rest-1", null, null, 0, "POSTED", null, attId, 0, 0, null, null
            )),
            wasteEvents = listOf(com.miara.cuentame.core.backup.model.WasteEventBackupDto(
                "w1", "rest-1", "i1", "a1", "uo1", "1", "1", "SPOILED", 0, null, attId, "POSTED", 0, 0, 0, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val result = planner.createPlan(makeRestaurant(), snapshotResult)
        
        assertThat(result).isInstanceOf(BackupPlanningResult.Failure::class.java)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.AttachmentsNotSupported)
    }
}
