package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.time.Duration.Companion.seconds
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
    fun `selecting file state is entered when onSelectFileClicked`() {
        viewModel.onSelectFileClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
    }

    @Test
    fun `inspecting state entered when file selected`() = runTest {
        coEvery { restoreRepository.inspect(any()) } coAnswers {
            delay(1.seconds)
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        }

        viewModel.onFileSelected("content://backup")
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
    }

    @Test
    fun `preview ready entered when inspection succeeds`() = runTest {
        val preview = mockk<com.miara.cuentame.core.model.backup.BackupRestorePreview>()
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Ready(mockk(), preview)

        viewModel.onFileSelected("uri")
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        val state = viewModel.uiState.value as BackupRestoreUiState.PreviewReady
        assertThat(state.preview).isEqualTo(preview)
    }

    @Test
    fun `error state entered when inspection fails`() = runTest {
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)

        viewModel.onFileSelected("uri")
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.Error::class.java)
        val state = viewModel.uiState.value as BackupRestoreUiState.Error
        assertThat(state.reason).isEqualTo(BackupRestoreFailure.InvalidZip)
    }

    @Test
    fun `dismiss request returns to idle`() {
        viewModel.onSelectFileClicked()
        viewModel.onDismissRequest()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `recreated active inspection becomes operation interrupted`() {
        val handle = SavedStateHandle(mapOf("inspection_active" to true))
        val vm = BackupRestoreViewModel(restoreRepository, handle)
        
        assertThat(vm.uiState.value).isInstanceOf(BackupRestoreUiState.Error::class.java)
        val state = vm.uiState.value as BackupRestoreUiState.Error
        assertThat(state.reason).isEqualTo(BackupRestoreFailure.OperationInterrupted)
        
        assertThat(handle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `stale result from cancelled operation is ignored`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreRepository.inspect(com.miara.cuentame.core.backup.api.BackupDocumentUri("uri-1")) } coAnswers { deferred1.await() }
        coEvery { restoreRepository.inspect(com.miara.cuentame.core.backup.api.BackupDocumentUri("uri-2")) } coAnswers { deferred2.await() }

        viewModel.onFileSelected("uri-1")
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        // Start second operation
        viewModel.onFileSelected("uri-2")
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        // Complete first operation LATE
        deferred1.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        // State should still be Inspecting (awaiting uri-2)
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        // Complete second operation
        val preview2 = mockk<com.miara.cuentame.core.model.backup.BackupRestorePreview>()
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(), preview2))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        val state = viewModel.uiState.value as BackupRestoreUiState.PreviewReady
        assertThat(state.preview).isEqualTo(preview2)
    }

    @Test
    fun `picker cancellation returns to idle`() {
        viewModel.onSelectFileClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        
        viewModel.onFileSelected(null)
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }
}
