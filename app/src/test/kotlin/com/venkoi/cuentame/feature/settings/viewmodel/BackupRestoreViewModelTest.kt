package com.venkoi.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.*
import com.venkoi.cuentame.core.model.backup.BackupRestoreEligibility
import com.venkoi.cuentame.core.model.backup.BackupRestoreFailure
import com.venkoi.cuentame.core.model.backup.BackupRestorePreview
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    private val restoreCoordinator = mockk<BackupRestoreCoordinator>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BackupRestoreViewModel
    private val savedStateHandle = SavedStateHandle()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restoreCoordinator.retryRecovery() } returns RestoreRecoveryResult.NoRecoveryNeeded
        coEvery { restoreCoordinator.startupState } returns MutableStateFlow(RestoreStartupState.Ready)
        viewModel = BackupRestoreViewModel(restoreCoordinator, savedStateHandle)
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
        val started = CompletableDeferred<Unit>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers {
            started.complete(Unit)
            delay(1.seconds)
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        }

        viewModel.onFileSelected("content://backup")
        runCurrent()
        started.await()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
    }

    @Test
    fun `preview ready entered when inspection succeeds`() = runTest {
        val preview = mockk<BackupRestorePreview>(relaxed = true)
        coEvery { restoreCoordinator.inspect(any()) } returns BackupArchiveInspectionResult.Ready(mockk(relaxed = true), preview, BackupRestoreEligibility.Eligible)

        viewModel.onFileSelected("uri")
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        val state = viewModel.uiState.value as BackupRestoreUiState.PreviewReady
        assertThat(state.preview).isEqualTo(preview)
        assertThat(state.eligibility).isEqualTo(BackupRestoreEligibility.Eligible)
    }

    @Test
    fun `error state entered when inspection fails`() = runTest {
        coEvery { restoreCoordinator.inspect(any()) } returns BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)

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
    fun `stale success is ignored`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val started2 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            started2.complete(Unit)
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onFileSelected("uri-2") // This cancels operation 1
        runCurrent()
        started2.await()
        
        deferred1.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>(relaxed = true)
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), preview2, BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        assertThat((viewModel.uiState.value as BackupRestoreUiState.PreviewReady).preview).isEqualTo(preview2)
    }

    @Test
    fun `stale typed failure is ignored`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onDismissRequest() // Cancel 1
        runCurrent()
        
        deferred1.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `stale typed failure cannot replace active newer inspection`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val started2 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            started2.complete(Unit)
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onFileSelected("uri-2")
        runCurrent()
        started2.await()
        
        deferred1.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>(relaxed = true)
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), preview2, BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        assertThat((viewModel.uiState.value as BackupRestoreUiState.PreviewReady).preview).isEqualTo(preview2)
    }

    @Test
    fun `stale exception is ignored`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onDismissRequest()
        runCurrent()
        
        deferred1.completeExceptionally(RuntimeException("Crash"))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `stale exception cannot replace active newer inspection`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val started2 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            started2.complete(Unit)
            deferred2.await()
        }

        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onFileSelected("uri-2")
        runCurrent()
        started2.await()
        
        deferred1.completeExceptionally(RuntimeException("Crash"))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>(relaxed = true)
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), preview2, BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
    }

    @Test
    fun `stale cancellation completion is ignored`() = runTest {
        val started1 = CompletableDeferred<Unit>()
        val deferred1 = CompletableDeferred<BackupArchiveInspectionResult>()
        val started2 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<BackupArchiveInspectionResult>()
        
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-1")) } coAnswers {
            withContext(NonCancellable) {
                started1.complete(Unit)
                deferred1.await()
            }
        }
        coEvery { restoreCoordinator.inspect(BackupDocumentUri("uri-2")) } coAnswers {
            started2.complete(Unit)
            deferred2.await()
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started1.await()
        
        viewModel.onFileSelected("uri-2")
        runCurrent()
        started2.await()
        
        deferred1.completeExceptionally(CancellationException())
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        val preview2 = mockk<BackupRestorePreview>(relaxed = true)
        deferred2.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), preview2, BackupRestoreEligibility.Eligible))
        runCurrent()
        
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
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onFileSelected(null)
        runCurrent()
        
        deferred.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late Ready after picker cancellation is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onFileSelected(null)
        runCurrent()
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `late ordinary exception after picker cancellation is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onFileSelected(null)
        runCurrent()
        
        deferred.completeExceptionally(RuntimeException("Crash"))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late CancellationException after picker cancellation does not leave Idle`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onFileSelected(null)
        runCurrent()
        
        deferred.completeExceptionally(CancellationException())
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late Ready after dismiss is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onDismissRequest()
        runCurrent()
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `late typed failure after dismiss is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onDismissRequest()
        runCurrent()
        
        deferred.complete(BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `late ordinary exception after dismiss is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onDismissRequest()
        runCurrent()
        
        deferred.completeExceptionally(RuntimeException("Crash"))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `choose another from preview returns to selecting file`() = runTest {
        coEvery { restoreCoordinator.inspect(any()) } returns BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible)
        
        viewModel.onFileSelected("uri-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        
        viewModel.onChooseAnotherClicked()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
    }

    @Test
    fun `choose another cancels active inspection`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers {
            started.complete(Unit)
            deferred.await()
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        viewModel.onChooseAnotherClicked()
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
    }

    @Test
    fun `choose another late result is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val deferred = CompletableDeferred<BackupArchiveInspectionResult>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers { 
            withContext(NonCancellable) {
                started.complete(Unit)
                deferred.await()
            } 
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        
        viewModel.onChooseAnotherClicked()
        runCurrent()
        
        deferred.complete(BackupArchiveInspectionResult.Ready(mockk(relaxed = true), mockk(relaxed = true), BackupRestoreEligibility.Eligible))
        runCurrent()
        
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.SelectingFile)
        assertThat(savedStateHandle.get<Boolean>("inspection_active")).isFalse()
    }

    @Test
    fun `dismiss from inspecting returns to idle`() = runTest {
        val started = CompletableDeferred<Unit>()
        coEvery { restoreCoordinator.inspect(any()) } coAnswers {
            started.complete(Unit)
            delay(1.seconds)
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        }
        
        viewModel.onFileSelected("uri-1")
        runCurrent()
        started.await()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
        
        viewModel.onDismissRequest()
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Idle)
    }

    @Test
    fun `recreated active inspection marker cleared after initialization`() = runTest {
        val handle = SavedStateHandle(mapOf("inspection_active" to true))
        coEvery { restoreCoordinator.retryRecovery() } returns RestoreRecoveryResult.NoRecoveryNeeded
        val vm = BackupRestoreViewModel(restoreCoordinator, handle)
        
        advanceUntilIdle()
        assertThat(handle.get<Boolean>("inspection_active")).isFalse()
    }
}
