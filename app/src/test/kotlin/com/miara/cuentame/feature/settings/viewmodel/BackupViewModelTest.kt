package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.test.*
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
        viewModel = createViewModel()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): BackupViewModel {
        return BackupViewModel(backupRepository, restaurantRepository, timeProvider, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeRestaurant(name: String = "My Rest") =
        Restaurant(RestaurantId("rest-1"), name, "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

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
            assertThat(event.operationId.value).isGreaterThan(0L)

            assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination(event.operationId))
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
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating(event.operationId))
    }

    @Test
    fun `onFileSelected with stale operationId is silently ignored`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()

        val event = awaitPickerEvent()
        val staleId = BackupOperationId(event.operationId.value - 1L)

        viewModel.onFileSelected(staleId, "uri-stale")

        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination(event.operationId))
        verify(exactly = 0) { backupRepository.createBackup(any()) }
    }

    @Test
    fun `onPickerCancelled with correct operationId transitions to Cancelled`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns makeRestaurant()

        val event = awaitPickerEvent()
        viewModel.onPickerCancelled(event.operationId)

        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Cancelled(event.operationId))
    }

    @Test
    fun `concurrent onCreateBackupRequested calls select exactly one token`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } coAnswers {
            delay(100)
            makeRestaurant()
        }

        viewModel.events.test {
            launch { viewModel.onCreateBackupRequested() }
            launch { viewModel.onCreateBackupRequested() }
            
            testDispatcher.scheduler.advanceUntilIdle()
            
            val item1 = awaitItem()
            expectNoEvents()
            
            assertThat(item1).isInstanceOf(BackupUiEvent.LaunchFilePicker::class.java)
        }
    }

    @Test
    fun `recreation from SavedStateHandle restores terminal states`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to 123L,
            "phase" to "CANCELLED"
        ))
        val vm = createViewModel(handle)
        assertThat(vm.uiState.value).isEqualTo(BackupUiState.Cancelled(BackupOperationId(123L)))
    }

    @Test
    fun `recreation from SavedStateHandle restores waiting state`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to 456L,
            "phase" to "WAITING"
        ))
        val vm = createViewModel(handle)
        assertThat(vm.uiState.value).isEqualTo(BackupUiState.WaitingForDestination(BackupOperationId(456L)))
    }

    @Test
    fun `recreation from SavedStateHandle reverts active states to interrupted error`() = runTest {
        for (phase in listOf("CREATING", "VALIDATING")) {
            val handle = SavedStateHandle(mapOf(
                "active_op_id" to 789L,
                "phase" to phase
            ))
            val vm = createViewModel(handle)
            val state = vm.uiState.value as BackupUiState.Error
            assertThat(state.operationId).isEqualTo(BackupOperationId(789L))
            assertThat(state.error).isEqualTo(BackupResult.Error.OperationInterrupted)
        }
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
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Cancelled(event1.operationId))

        viewModel.resetState()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)

        val event2 = awaitPickerEvent()
        viewModel.onFileSelected(event2.operationId, "uri-new")
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value as BackupUiState.Success
        assertThat(success.manifest).isEqualTo(manifest)
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

        viewModel.resetState()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)

        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
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
        assertThat(err.error).isInstanceOf(BackupResult.Error.FilenamePreparationFailure::class.java)
    }
}
