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
        every { appVersionProvider.databaseSchemaVersion } returns 2
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
        assertThat(plan.totalUncompressedBytes).isGreaterThan(0L)
        assertThat(plan.expectedEntryChecksums.keys).containsExactly("data/database.json", "preferences/settings.json", "manifest.json")
    }

    @Test
    fun `rejects conflicting attachment bindings`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = createValidSnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "pr1", "rest-1", null, null, 0, "DRAFT", null, "id1", 0, 0, null, null
            ))
        )
        
        val bindings = listOf(
            BackupAttachmentSourceBinding("id1", AttachmentSourceUri("uri1")),
            BackupAttachmentSourceBinding("id1", AttachmentSourceUri("uri2"))
        )
        
        val result = planner.createPlan(makeRestaurant(), BackupSnapshotResult(snapshotDto, bindings))
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.ConflictingAttachmentSource)
    }

    @Test
    fun `rejects plan if overlong entry name generated`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val longId = "i".repeat(240) 
        val snapshotDto = createValidSnapshotDto().copy(
            purchaseReceipts = listOf(com.miara.cuentame.core.backup.model.PurchaseReceiptBackupDto(
                "pr1", "rest-1", null, null, 0, "DRAFT", null, longId, 0, 0, null, null
            ))
        )
        val bindings = listOf(BackupAttachmentSourceBinding(longId, AttachmentSourceUri("uri1")))
        attachmentSource.metadataMap[AttachmentSourceUri("uri1")] = AttachmentSourceMetadata(AttachmentSourceUri("uri1"), "n.jpg", "image/jpeg")
        attachmentSource.dataMap[AttachmentSourceUri("uri1")] = "d".toByteArray()

        val result = planner.createPlan(makeRestaurant(), BackupSnapshotResult(snapshotDto, bindings))
        assertThat(result).isInstanceOf(BackupPlanningResult.Failure::class.java)
        assertThat((result as BackupPlanningResult.Failure).reason).isEqualTo(BackupPlanningFailure.EntryNameLimitExceeded)
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
}
