package com.miara.cuentame.feature.settings.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val backupRepository = mockk<BackupRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val timeProvider = mockk<TimeProvider>()

    private lateinit var viewModel: BackupViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BackupViewModel(backupRepository, restaurantRepository, timeProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeRestaurant(name: String = "My Rest") =
        Restaurant(RestaurantId("rest-1"), name, "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

    // ── Helper: advances until idle and collects the first LaunchFilePicker event ──

    private suspend fun awaitPickerEvent(): BackupUiEvent.LaunchFilePicker {
        var event: BackupUiEvent.LaunchFilePicker? = null
        viewModel.events.test {
            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()
            event = awaitItem() as BackupUiEvent.LaunchFilePicker
            cancelAndConsumeRemainingEvents()
        }
        return event!!
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }

    @Test
    fun `onCreateBackupRequested emits LaunchFilePicker with operationId and transitions to WaitingForDestination`() = runTest {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        every { timeProvider.now() } returns now
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant("My Rest")

        viewModel.events.test {
            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as BackupUiEvent.LaunchFilePicker
            assertThat(event.suggestedName).contains("My_Rest")
            assertThat(event.suggestedName).contains("2026-01-01")
            assertThat(event.operationId).isGreaterThan(0L)

            assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
        }
    }

    @Test
    fun `onFileSelected with correct operationId transitions to Creating`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { backupRepository.createBackup("uri-ok") } returns flow {
            emit(BackupOperationStatus.Creating)
        }

        val event = awaitPickerEvent()
        viewModel.onFileSelected(event.operationId, "uri-ok")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating)
    }

    @Test
    fun `onFileSelected with stale operationId is silently ignored`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()

        val event = awaitPickerEvent()
        val staleId = event.operationId - 1L

        viewModel.onFileSelected(staleId, "uri-stale")

        // State must still be WaitingForDestination — not Creating
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
        verify(exactly = 0) { backupRepository.createBackup(any()) }
    }

    @Test
    fun `onPickerCancelled with correct operationId transitions to Cancelled`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } coAnswers {
            delay(1000)
            makeRestaurant()
        }
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceTimeBy(10)
        val token = viewModel.uiState.value // WaitingForDestination
        assertThat(token).isEqualTo(BackupUiState.WaitingForDestination)

        // Token comes from the next event (emitted after delay resolves), but we
        // can cancel before the event fires. For simplicity retrieve via reflection.
        val operationTokenField = viewModel.javaClass.getDeclaredField("activeOperationToken")
        operationTokenField.isAccessible = true
        val opId = operationTokenField.getLong(viewModel)

        viewModel.onPickerCancelled(opId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Cancelled)
    }

    @Test
    fun `onPickerCancelled with stale operationId is silently ignored`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        awaitPickerEvent()

        // Pass stale id
        viewModel.onPickerCancelled(-999L)

        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
    }

    @Test
    fun `duplicate simultaneous file callbacks are rejected via CAS`() = runTest {
        val flow = flow { emit(BackupOperationStatus.Creating) }
        every { backupRepository.createBackup("accepted-uri") } returns flow
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        val event = awaitPickerEvent()

        // First callback succeeds
        viewModel.onFileSelected(event.operationId, "accepted-uri")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating)

        // Duplicate callback while Creating is rejected immediately
        viewModel.onFileSelected(event.operationId, "duplicate-uri")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating)

        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { backupRepository.createBackup("accepted-uri") }
        verify(exactly = 0) { backupRepository.createBackup("duplicate-uri") }
    }

    @Test
    fun `stale backup flow emission is ignored after new token`() = runTest {
        val slowFlow = flow {
            emit(BackupOperationStatus.Creating)
            delay(2000)
            emit(BackupOperationStatus.Success(mockk()))
        }
        every { backupRepository.createBackup("uri-slow") } returns slowFlow
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        val event = awaitPickerEvent()
        viewModel.onFileSelected(event.operationId, "uri-slow")
        testDispatcher.scheduler.advanceTimeBy(500)

        // Reset state (invalidating active token)
        viewModel.resetState()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)

        testDispatcher.scheduler.advanceUntilIdle()
        // Stale flow finishes late, state must remain Idle
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }

    @Test
    fun `reset followed by new operation succeeds cleanly`() = runTest {
        val manifest = mockk<BackupManifest>()
        every { backupRepository.createBackup("uri-new") } returns flowOf(
            BackupOperationStatus.Creating,
            BackupOperationStatus.Success(manifest)
        )
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        val event1 = awaitPickerEvent()
        viewModel.onPickerCancelled(event1.operationId)
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Cancelled)

        viewModel.resetState()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)

        val event2 = awaitPickerEvent()
        viewModel.onFileSelected(event2.operationId, "uri-new")
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value as BackupUiState.Success
        assertThat(success.manifest).isEqualTo(manifest)
    }

    @Test
    fun `cancellation exception is not mapped to error state`() = runTest {
        val cancellingFlow = flow<BackupOperationStatus> {
            emit(BackupOperationStatus.Creating)
            throw CancellationException("Cancelled by caller")
        }
        every { backupRepository.createBackup("uri-cancel") } returns cancellingFlow
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        val event = awaitPickerEvent()
        viewModel.onFileSelected(event.operationId, "uri-cancel")
        testDispatcher.scheduler.advanceUntilIdle()

        // State must not be Error
        assertThat(viewModel.uiState.value).isNotInstanceOf(BackupUiState.Error::class.java)
    }

    @Test
    fun `filename preparation error maps to FilenamePreparationFailure`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws RuntimeException("DB offline")
        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(BackupUiState.Error::class.java)
        val err = state as BackupUiState.Error
        assertThat(err.result).isInstanceOf(BackupResult.Error.FilenamePreparationFailure::class.java)
    }

    @Test
    fun `concurrent onFileSelected calls across separate coroutines select exactly one`() = runTest {
        val flow1 = flow { emit(BackupOperationStatus.Creating); delay(1000) }
        val flow2 = flow { emit(BackupOperationStatus.Creating); delay(1000) }
        every { backupRepository.createBackup("uri-1") } returns flow1
        every { backupRepository.createBackup("uri-2") } returns flow2
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { timeProvider.now() } returns Instant.EPOCH

        val event = awaitPickerEvent()

        val j1 = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            viewModel.onFileSelected(event.operationId, "uri-1")
        }
        val j2 = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            viewModel.onFileSelected(event.operationId, "uri-2")
        }
        testDispatcher.scheduler.advanceUntilIdle()
        j1.join()
        j2.join()

        verify(exactly = 1) { backupRepository.createBackup("uri-1") }
        verify(exactly = 0) { backupRepository.createBackup("uri-2") }
    }

    @Test
    fun `stale picker result from configuration change is rejected`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()
        every { backupRepository.createBackup(any()) } returns flow {
            emit(BackupOperationStatus.Creating)
        }

        val event = awaitPickerEvent()
        val staleOpId = event.operationId - 5L // Simulate a very old token

        // Stale callback must not change state or call repository
        viewModel.onFileSelected(staleOpId, "uri-from-old-activity")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
        verify(exactly = 0) { backupRepository.createBackup(any()) }

        // A cancellation with stale ID must also be rejected
        viewModel.onPickerCancelled(staleOpId)
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
    }
}
