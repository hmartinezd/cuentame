package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview
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
        val preview = mockk<BackupRestorePreview>()
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
    fun `stale success is ignored`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected("uri-2")
        
        deferred1.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>()
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(), preview2))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        assertThat((viewModel.uiState.value as BackupRestoreUiState.PreviewReady).preview).isEqualTo(preview2)
    }

    @Test
    fun `stale typed failure is ignored`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onDismissRequest()
        
        deferred1.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `stale typed failure cannot replace active newer inspection`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected("uri-2")
        
        deferred1.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>()
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(), preview2))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        assertThat((viewModel.uiState.value as BackupRestoreUiState.PreviewReady).preview).isEqualTo(preview2)
    }

    @Test
    fun `stale exception is ignored`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onDismissRequest()
        
        deferred1.completeExceptionally(RuntimeException("Crash"))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `stale exception cannot replace active newer inspection`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected("uri-2")
        
        deferred1.completeExceptionally(RuntimeException("Crash"))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>()
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(), preview2))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
    }

    @Test
    fun `stale cancellation completion is ignored`() = runTest {
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) { deferred1.await() }
        }
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            deferred2.await()
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected("uri-2")
        
        deferred1.completeExceptionally(CancellationException())
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>()
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(), preview2))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
    }

    @Test
    fun `picker cancellation returns to idle`() {
        viewModel.onSelectFileClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        
        viewModel.onFileSelected(null)
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `late failure after picker cancellation is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected(null)
        
        deferred.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late Ready after picker cancellation is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected(null)
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `late ordinary exception after picker cancellation is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected(null)
        
        deferred.completeExceptionally(RuntimeException("Crash"))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late CancellationException after picker cancellation does not leave Idle`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onFileSelected(null)
        
        deferred.completeExceptionally(CancellationException())
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late Ready after dismiss is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onDismissRequest()
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `late typed failure after dismiss is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onDismissRequest()
        
        deferred.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late ordinary exception after dismiss is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onDismissRequest()
        
        deferred.completeExceptionally(RuntimeException("Crash"))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `choose another from preview returns to selecting file`() = runTest {
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Ready(mockk(), mockk())
        
        viewModel.onFileSelected("uri-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        
        viewModel.onChooseAnotherClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
    }

    @Test
    fun `choose another cancels active inspection`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(any()) } coAnswers { deferred.await() }
        
        viewModel.onFileSelected("uri-1")
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        viewModel.onChooseAnotherClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
    }

    @Test
    fun `choose another late result is ignored`() = runTest {
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreRepository.inspect(BackupDocumentUri("uri-1")) } coAnswers { 
            withContext(NonCancellable) { deferred.await() } 
        }
        
        viewModel.onFileSelected("uri-1")
        viewModel.onChooseAnotherClicked()
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(), mockk()))
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `dismiss from inspecting returns to idle`() = runTest {
        coEvery { restoreRepository.inspect(any()) } coAnswers { delay(1.seconds); BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip) }
        
        viewModel.onFileSelected("uri-1")
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        viewModel.onDismissRequest()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `recreated active inspection marker cleared after initialization`() {
        val handle = SavedStateHandle(mapOf("inspection_active" to true))
        BackupRestoreViewModel(restoreRepository, handle)
        
        assertThat(handle.get<Boolean>("inspection_active")).isFalse()
        
        val vm2 = BackupRestoreViewModel(restoreRepository, handle)
        assertThat(vm2.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }
}
