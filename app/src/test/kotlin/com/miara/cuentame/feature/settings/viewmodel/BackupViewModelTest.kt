package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.diagnostic.PilotDiagnosticExporter
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val backupRepository = mockk<BackupRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val diagnosticExporter = mockk<PilotDiagnosticExporter>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): BackupViewModel {
        return BackupViewModel(
            backupRepository,
            restaurantRepository,
            preferencesRepository,
            diagnosticExporter,
            timeProvider,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }

    @Test
    fun `two simultaneous create requests only start one operation`() = runTest {
        val viewModel = createViewModel()
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)

        viewModel.onCreateBackupRequested()
        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { timeProvider.now() }
        assertThat(viewModel.uiState.value).isInstanceOf(BackupUiState.WaitingForDestination::class.java)
    }

    @Test
    fun `reset cancels active preparation job`() = runTest {
        val viewModel = createViewModel()
        val preparationFlow = MutableSharedFlow<Unit>()
        coEvery { restaurantRepository.getRestaurant() } coAnswers {
            preparationFlow.test { awaitItem() } // Hang until we emit or cancel
            mockk(relaxed = true)
        }
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.runCurrent()
        
        viewModel.resetStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
        verify(exactly = 0) { timeProvider.now() } // Should be cancelled before completion
    }

    @Test
    fun `restored CREATING state shows OperationInterrupted error`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to 123L,
            "phase" to "CREATING"
        ))
        val viewModel = createViewModel(handle)
        
        val state = viewModel.uiState.value as BackupUiState.Error
        assertThat(state.operationId).isEqualTo(BackupOperationId(123L))
        assertThat(state.error).isEqualTo(BackupResult.Error.OperationInterrupted)
    }

    @Test
    fun `stale repository emissions are ignored after reset`() = runTest {
        val repositoryFlow = MutableSharedFlow<BackupOperationStatus>(extraBufferCapacity = 1)
        coEvery { backupRepository.createBackup(any()) } returns repositoryFlow
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val opId = (viewModel.uiState.value as BackupUiState.WaitingForDestination).operationId
        viewModel.consumePickerLaunch(opId)
        viewModel.onFileSelected(opId, "uri")
        testDispatcher.scheduler.runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating(opId))
        
        viewModel.resetStatus()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
        
        repositoryFlow.tryEmit(BackupOperationStatus.Success(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }
    
    @Test
    fun `interrupted survives second recreation`() = runTest {
        val handle1 = SavedStateHandle(mapOf(
            "active_op_id" to 1L,
            "phase" to "CREATING"
        ))
        val viewModel1 = createViewModel(handle1)
        assertThat(viewModel1.uiState.value).isInstanceOf(BackupUiState.Error::class.java)
        
        // Simulate what restoreState does: it persists INTERRUPTED phase
        val handle2 = SavedStateHandle(mapOf(
            "last_op_id" to 1L,
            "phase" to "INTERRUPTED"
        ))
        val viewModel2 = createViewModel(handle2)
        assertThat(viewModel2.uiState.value).isInstanceOf(BackupUiState.Error::class.java)
        assertThat((viewModel2.uiState.value as BackupUiState.Error).operationId).isEqualTo(BackupOperationId(1L))
    }

    @Test
    fun `malformed WAITING state with active ID -1 becomes Idle`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to -1L,
            "phase" to "WAITING"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }

    @Test
    fun `stale file selection is rejected`() = runTest {
        val viewModel = createViewModel()
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)

        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val staleOpId = BackupOperationId(999L)
        viewModel.onFileSelected(staleOpId, "uri")
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupUiState.WaitingForDestination::class.java)
    }
}
