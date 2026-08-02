package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.Instant

class BackupMetadataParityTest {

    private val localeReconciler = mockk<AppLocaleReconciler>()
    private val preferencesSource = FakeBackupPreferencesSource()
    private val attachmentSource = FakeBackupAttachmentSource()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val jsonCodecs = BackupJsonCodecs()

    private lateinit var planner: BackupCreationPlanner
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
        validator = DefaultBackupArchiveValidator(jsonCodecs)

        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 4
    }

    @Test
    fun `planner and validator agree on schema 4 metadata`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        var snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto("r1", "Rest", "USD", "en-US", 0, 0, null))
        )
        snapshotDto = BackupTestFixtures.addPostedPurchase(
            snapshotDto, "p1", "pl1", "m1", "i1", "a1", "o1", BigDecimal("10"), BigDecimal("5"), 1000L, 1000L
        )
        snapshotDto = BackupTestFixtures.addPostedProduction(
            snapshotDto, "pb1", "m2", "i2", "a1", "o2", BigDecimal("5"), BigDecimal("20"), 2000L, 2000L
        )
        
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val restaurant = Restaurant(RestaurantId("r1"), "Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val planResult = planner.createPlan(restaurant, snapshotResult)
        
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 1. Validate Planner's metadata
        val plannerMetadata = plan.manifest.tableMetadata
        assertThat(plannerMetadata.keys).containsExactlyElementsIn(BackupFormatV1Contract.expectedTablesForSchema(4))
        
        // 2. Build archive from plan
        val archiveBytes = buildArchive(plan)
        
        // 3. Validate through Validator
        val validationResult = validator.validate(ByteArrayInputStream(archiveBytes))
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        val validatedManifest = (validationResult as BackupValidationResult.Valid).manifest
        
        // 4. Compare
        assertThat(validatedManifest.tableMetadata).isEqualTo(plannerMetadata)
        
        // Assert specific derived flags
        assertThat(plannerMetadata["inventory_balance_projections"]?.isDerived).isTrue()
        assertThat(plannerMetadata["ingredient_cost_projections"]?.isDerived).isTrue()
        assertThat(plannerMetadata["ingredients"]?.isDerived).isFalse()
    }

    private fun buildArchive(plan: BackupPlan): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(bos)

        fun add(name: String, content: ByteArray) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()
        }

        add(BackupFormatV1Contract.MANIFEST_ENTRY, plan.manifestJson.copyForTest())
        add(BackupFormatV1Contract.DATABASE_ENTRY, plan.snapshotJson.copyForTest())
        add(BackupFormatV1Contract.PREFERENCES_ENTRY, plan.preferencesJson.copyForTest())
        add(BackupFormatV1Contract.CHECKSUMS_ENTRY, plan.checksumsJson.copyForTest())

        zos.close()
        return bos.toByteArray()
    }
}
