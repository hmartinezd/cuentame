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
    }

    @Test
    fun `complete round trip with shared attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val attId = "0123456789abcdef"
        val attUri = AttachmentSourceUri("content://shared")
        val attData = "shared data".toByteArray()
        attachmentSource.metadataMap[attUri] = AttachmentSourceMetadata(attUri, "file.jpg", "image/jpeg")
        attachmentSource.dataMap[attUri] = attData

        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto("rest-1", "Test Restaurant", "USD", "en-US", 0L, 0L, null)),
            // Shared between purchase and waste
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto("p1", "rest-1", null, null, 0, "POSTED", null, attId, 1000, 1000, 1000, null)),
            wasteEvents = listOf(com.miara.cuentame.core.backup.model.WasteEventBackupDto("w1", "rest-1", "i1", "a1", "o1", "1.0", "1.0", "SPOILED", 1000, null, attId, "POSTED", 1000, 1000, 1000, null)),
            // Prereqs
            ingredients = listOf(com.miara.cuentame.core.backup.model.IngredientBackupDto("i1", "rest-1", "I", "i", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            inventoryAreas = listOf(com.miara.cuentame.core.backup.model.InventoryAreaBackupDto("a1", "rest-1", "A", "a", 0, true, 0, 0, null)),
            units = listOf(com.miara.cuentame.core.backup.model.UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 0)),
            ingredientUnitOptions = listOf(com.miara.cuentame.core.backup.model.IngredientUnitOptionBackupDto("o1", "i1", "O", "o", null, "1.0", true, true, true, true, 0, 0, null)),
            inventoryMovements = listOf(
                com.miara.cuentame.core.backup.model.InventoryMovementBackupDto("m1", "rest-1", "i1", "a1", "PURCHASE", "1.0", "1.0", "1.0", 1000L, "PURCHASE_RECEIPT", "p1", "op1", "l1", null, 1000L),
                com.miara.cuentame.core.backup.model.InventoryMovementBackupDto("m2", "rest-1", "i1", "a1", "WASTE", "-1.0", "1.0", "-1.0", 1000L, "WASTE_EVENT", "w1", "op2", "w1", null, 1000L)
            ),
            purchaseLines = listOf(com.miara.cuentame.core.backup.model.PurchaseLineBackupDto("l1", "p1", "i1", "a1", "o1", "1.0", "1.0", "1.0", "1.0", null, 0, 0)),
            inventoryBalanceProjections = listOf(com.miara.cuentame.core.backup.model.InventoryBalanceProjectionBackupDto("rest-1", "i1", "a1", "0.0", 0L)),
            ingredientCostProjections = listOf(com.miara.cuentame.core.backup.model.IngredientCostProjectionBackupDto("rest-1", "i1", "1.0", 0L))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, listOf(BackupAttachmentSourceBinding(attId, attUri)))

        val planResult = planner.createPlan(makeRestaurant(), snapshotResult)
        if (planResult is BackupPlanningResult.Failure) throw Exception("Planning failed: ${planResult.reason}")
        
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        assertThat(plan.attachments).hasSize(1)
        assertThat(plan.attachments.first().references).hasSize(2)

        val output = ByteArrayOutputStream()
        writer.write(output, plan)
        
        val validation = validator.validate(ByteArrayInputStream(output.toByteArray()))
        if (validation is BackupValidationResult.Invalid) throw Exception("Validation failed: ${validation.code} ${validation.diagnostic}")
        assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)
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
        
        writer.write(out1, plan1)
        writer.write(out2, plan2)

        assertThat(out1.toByteArray()).isEqualTo(out2.toByteArray())
    }
}
