package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    private val restoreRepository = mockk<BackupRestoreRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BackupRestoreViewModel
    private val savedStateHandle = SavedStateHandle()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BackupRestoreViewModel(restoreRepository, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `recreated active inspection becomes operation interrupted`() {
        val handle = SavedStateHandle(mapOf("inspection_active" to true))
        val vm = BackupRestoreViewModel(restoreRepository, handle)
        
        assertThat(vm.uiState.value).isInstanceOf(BackupRestoreUiState.Error::class.java)
        val state = vm.uiState.value as BackupRestoreUiState.Error
        assertThat(state.reason).isEqualTo(BackupRestoreFailure.OperationInterrupted)
        
        // Interruption should be cleared
        assertThat(handle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `inspecting state entered when file selected`() = runTest {
        coEvery { restoreRepository.inspect(any()) } coAnswers {
            delay(1000)
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        }

        viewModel.onFileSelected("content://backup")
        
        // Use advanceTimeBy if using StandardTestDispatcher
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
    }

    @Test
    fun `stale result from cancelled operation is ignored`() = runTest {
        coEvery { restoreRepository.inspect(any()) } coAnswers {
            delay(1000)
            BackupArchiveInspectionResult.Ready(mockk(), mockk())
        }

        viewModel.onFileSelected("uri-1")
        advanceTimeBy(500)
        
        // Start second operation
        viewModel.onFileSelected("uri-2")
        
        // Complete first operation in background
        advanceTimeBy(600)
        
        // State should still be Inspecting (for uri-2) or ready for uri-2 if finished
        // Point is uri-1's result shouldn't have set state to PreviewReady
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
    }

    @Test
    fun `picker cancellation returns to idle`() {
        viewModel.onSelectFileClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        
        viewModel.onFileSelected(null)
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }
}
