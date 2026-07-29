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
        every { appVersionProvider.databaseSchemaVersion } returns 2
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
    fun `complete round trip without attachments`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto(
                "rest-1", "Test Restaurant", "USD", "en-US", 0L, 0L, null
            ))
        )
        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        // 1. Plan
        val planningResult = planner.createPlan(makeRestaurant(), snapshotResult)
        assertThat(planningResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planningResult as BackupPlanningResult.Success).plan

        // 2. Write
        val output = ByteArrayOutputStream()
        val writeResult = writer.write(output, plan)
        if (writeResult is BackupArchiveWriteResult.Failure.IoError) {
             throw writeResult.cause
        }
        assertThat(writeResult).isEqualTo(BackupArchiveWriteResult.Success)

        // 3. Validate
        val validationResult = validator.validate(ByteArrayInputStream(output.toByteArray()))
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        
        val valid = validationResult as BackupValidationResult.Valid
        assertThat(valid.manifest.restaurantId).isEqualTo(plan.manifest.restaurantId)
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
