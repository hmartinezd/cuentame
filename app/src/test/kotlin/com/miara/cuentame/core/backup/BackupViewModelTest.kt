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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `recreation with pending launch re-emits event exactly once`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to 123L,
            "phase" to "WAITING",
            "picker_state" to "PENDING",
            "suggested_name" to "Suggested.zip"
        ))
        
        val viewModel = createViewModel(handle)
        
        viewModel.events.test {
            val event = awaitItem() as BackupUiEvent.LaunchFilePicker
            assertThat(event.operationId).isEqualTo(BackupOperationId(123L))
            assertThat(event.suggestedName).isEqualTo("Suggested.zip")
            expectNoEvents()
        }
    }

    @Test
    fun `consumePickerLaunch prevents multiple launches for same ID`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(handle)
        
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns mockk(relaxed = true)
        
        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val opId = (viewModel.uiState.value as BackupUiState.WaitingForDestination).operationId
        
        assertThat(viewModel.consumePickerLaunch(opId)).isTrue()
        assertThat(viewModel.consumePickerLaunch(opId)).isFalse()
    }

    @Test
    fun `recreation in CREATING phase restores as INTERRUPTED error`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "active_op_id" to 789L,
            "phase" to "CREATING"
        ))
        val viewModel = createViewModel(handle)
        
        val state = viewModel.uiState.value as BackupUiState.Error
        assertThat(state.operationId).isEqualTo(BackupOperationId(789L))
        assertThat(state.error).isEqualTo(BackupResult.Error.OperationInterrupted)
        
        // Ensure active operation is cleared
        viewModel.onFileSelected(BackupOperationId(789L), "uri")
        assertThat(viewModel.uiState.value).isInstanceOf(BackupUiState.Error::class.java)
    }
}
