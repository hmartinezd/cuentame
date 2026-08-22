package com.venkoi.restaurantops.feature.areas.viewmodel

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreview
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreviewLoader
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreviewResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictResolutionResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictResolver
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncService
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryAreaManualSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val restaurants = mockk<RestaurantRepository>()
    private val sync = mockk<InventoryAreaSyncService>()
    private val resolver = mockk<InventoryAreaConflictResolver>()
    private val previews = mockk<InventoryAreaConflictPreviewLoader>()
    private lateinit var viewModel: InventoryAreaManualSyncViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { restaurants.getRestaurant() } returns RESTAURANT
        viewModel = InventoryAreaManualSyncViewModel(restaurants, sync, resolver, previews)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `sync now succeeds and rapid duplicate taps invoke one sync`() = runTest {
        val pending = CompletableDeferred<InventoryAreaSyncResult>()
        coEvery { sync.sync(RESTAURANT_ID) } coAnswers { pending.await() }

        viewModel.syncNow(); viewModel.syncNow(); runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Syncing)
        coVerify(exactly = 1) { sync.sync(RESTAURANT_ID) }
        pending.complete(InventoryAreaSyncResult.Success(1, 1, 2)); advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Success)
    }

    @Test fun `conflict retains exact operation and requests local cloud preview`() = runTest {
        coEvery { sync.sync(RESTAURANT_ID) } returns conflict("operation-x")
        coEvery { previews.load(any()) } returns InventoryAreaConflictPreviewResult.Available(PREVIEW)

        viewModel.syncNow(); advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryAreaManualSyncUiState.Conflict
        assertThat(state.conflict.operationId).isEqualTo("operation-x")
        assertThat(state.preview).isEqualTo(PREVIEW)
        assertThat(state.preview.local.name).isEqualTo("Local")
        assertThat(state.preview.cloud.name).isEqualTo("Cloud")
        coVerify(exactly = 1) { previews.load(state.conflict) }
    }

    @Test fun `preview failure disables resolution and can retry`() = runTest {
        coEvery { sync.sync(RESTAURANT_ID) } returns conflict("operation-x")
        coEvery { previews.load(any()) } returnsMany listOf(
            InventoryAreaConflictPreviewResult.RemoteFailure(PREVIEW.local),
            InventoryAreaConflictPreviewResult.Available(PREVIEW)
        )
        viewModel.syncNow(); advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(InventoryAreaManualSyncUiState.PreviewUnavailable::class.java)

        viewModel.useThisDevice(); viewModel.useCloudVersion(); advanceUntilIdle()
        coVerify(exactly = 0) { resolver.resolveKeepLocal(any()) }
        coVerify(exactly = 0) { resolver.resolveUseCloud(any()) }
        viewModel.retryPreview(); advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(InventoryAreaManualSyncUiState.Conflict::class.java)
    }

    @Test fun `use this device resolves once syncs replacement and closes conflict`() = runTest {
        surfaceConflict("old-operation")
        coEvery { resolver.resolveKeepLocal(any()) } returns
            InventoryAreaConflictResolutionResult.KeepLocalPrepared(AREA_ID.value, "new-operation", 5)
        coEvery { sync.sync(RESTAURANT_ID) } returns InventoryAreaSyncResult.Success(1, 1, 8)

        viewModel.useThisDevice(); advanceUntilIdle()

        coVerify(exactly = 1) { resolver.resolveKeepLocal(match { it.operationId == "old-operation" }) }
        coVerify(exactly = 1) { sync.sync(RESTAURANT_ID) }
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Success)
    }

    @Test fun `keep local remote race replaces conflict reference and comparison`() = runTest {
        surfaceConflict("old-operation")
        coEvery { resolver.resolveKeepLocal(any()) } returns
            InventoryAreaConflictResolutionResult.KeepLocalPrepared(AREA_ID.value, "replacement", 5)
        coEvery { sync.sync(RESTAURANT_ID) } returns conflict("replacement")
        coEvery { previews.load(match { it.operationId == "replacement" }) } returns
            InventoryAreaConflictPreviewResult.Available(PREVIEW.copy(cloud = CLOUD.copy(name = "Cloud Again")))

        viewModel.useThisDevice(); advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryAreaManualSyncUiState.Conflict
        assertThat(state.conflict.operationId).isEqualTo("replacement")
        assertThat(state.preview.cloud.name).isEqualTo("Cloud Again")
    }

    @Test fun `use cloud resolves once continues sync and constraint keeps conflict available`() = runTest {
        surfaceConflict("cloud-operation")
        coEvery { resolver.resolveUseCloud(any()) } returns
            InventoryAreaConflictResolutionResult.LocalConstraintConflict(AREA_ID.value, "other")

        viewModel.useCloudVersion(); advanceUntilIdle()
        var state = viewModel.uiState.value as InventoryAreaManualSyncUiState.Conflict
        assertThat(state.message).isEqualTo(InventoryAreaConflictMessage.LOCAL_CONSTRAINT)
        assertThat(state.conflictingEntityId).isEqualTo("other")
        coVerify(exactly = 1) { resolver.resolveUseCloud(any()) }
        coVerify(exactly = 0) { sync.sync(any()) }

        coEvery { resolver.resolveUseCloud(any()) } returns
            InventoryAreaConflictResolutionResult.CloudAccepted(AREA_ID.value, 6, 9)
        coEvery { sync.sync(RESTAURANT_ID) } returns InventoryAreaSyncResult.Success(0, 1, 9)
        viewModel.useCloudVersion(); advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Success)
        coVerify(exactly = 2) { resolver.resolveUseCloud(any()) }
        coVerify(exactly = 1) { sync.sync(RESTAURANT_ID) }
    }

    @Test fun `stale conflict dismisses old reference and fresh sync is initiated`() = runTest {
        surfaceConflict("stale-operation")
        coEvery { resolver.resolveKeepLocal(any()) } returns InventoryAreaConflictResolutionResult.StaleConflict
        coEvery { sync.sync(RESTAURANT_ID) } returns InventoryAreaSyncResult.Success(0, 0, 4)

        viewModel.useThisDevice(); advanceUntilIdle()

        coVerify(exactly = 1) { resolver.resolveKeepLocal(match { it.operationId == "stale-operation" }) }
        coVerify(exactly = 1) { sync.sync(RESTAURANT_ID) }
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Success)
    }

    @Test fun `resolver remote failure keeps conflict retryable and cancel mutates nothing`() = runTest {
        surfaceConflict("durable-operation")
        coEvery { resolver.resolveKeepLocal(any()) } returns InventoryAreaConflictResolutionResult.RemoteFailure
        viewModel.useThisDevice(); advanceUntilIdle()
        val state = viewModel.uiState.value as InventoryAreaManualSyncUiState.Conflict
        assertThat(state.message).isEqualTo(InventoryAreaConflictMessage.REMOTE_FAILURE)
        assertThat(state.conflict.operationId).isEqualTo("durable-operation")

        clearMocks(sync, resolver, answers = false, recordedCalls = true)
        viewModel.dismissConflict(); advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(InventoryAreaManualSyncUiState.Idle)
        coVerify(exactly = 0) { resolver.resolveKeepLocal(any()) }
        coVerify(exactly = 0) { resolver.resolveUseCloud(any()) }
        coVerify(exactly = 0) { sync.sync(any()) }
    }

    private suspend fun surfaceConflict(operationId: String) {
        coEvery { sync.sync(RESTAURANT_ID) } returns conflict(operationId)
        coEvery { previews.load(any()) } returns InventoryAreaConflictPreviewResult.Available(PREVIEW)
        viewModel.syncNow(); dispatcher.scheduler.advanceUntilIdle()
        clearMocks(sync, resolver, previews, answers = false, recordedCalls = true)
    }

    private fun conflict(operationId: String) = InventoryAreaSyncResult.Conflict(AREA_ID.value, operationId, 5, 8)

    private companion object {
        val RESTAURANT_ID = RestaurantId("restaurant")
        val AREA_ID = InventoryAreaId("area")
        val RESTAURANT = Restaurant(RESTAURANT_ID, "Restaurant", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val LOCAL = InventoryArea(AREA_ID, RESTAURANT_ID, "Local", "local", 0, true, Instant.EPOCH, Instant.EPOCH)
        val CLOUD = LOCAL.copy(name = "Cloud", normalizedName = "cloud", sortOrder = 1, isActive = false, deletedAt = Instant.EPOCH)
        val PREVIEW = InventoryAreaConflictPreview(LOCAL, CLOUD)
    }
}
