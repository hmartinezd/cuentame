package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): BackupViewModel {
        return BackupViewModel(backupRepository, restaurantRepository, timeProvider, savedStateHandle)
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
}
