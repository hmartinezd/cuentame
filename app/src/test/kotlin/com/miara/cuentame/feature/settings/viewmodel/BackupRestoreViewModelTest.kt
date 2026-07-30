package com.miara.cuentame.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview
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
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: BackupRestoreViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BackupRestoreViewModel(restoreRepository, SavedStateHandle())
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
        val uri = "content://backup"
        coEvery { restoreRepository.inspect(any()) } coAnswers {
            delay(100)
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        }

        viewModel.onFileSelected(uri)
        assertThat(viewModel.uiState.value).isEqualTo(BackupRestoreUiState.Inspecting)
    }

    @Test
    fun `preview ready entered when inspection succeeds`() = runTest {
        val preview = BackupRestorePreview("Rest", null, 1, 2, "en-US", emptyMap(), 0, 0)
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Ready(mockk(), preview)

        viewModel.onFileSelected("uri")
        
        assertThat(viewModel.uiState.value).isInstanceOf(BackupRestoreUiState.PreviewReady::class.java)
        val state = viewModel.uiState.value as BackupRestoreUiState.PreviewReady
        assertThat(state.preview).isEqualTo(preview)
    }

    @Test
    fun `error state entered when inspection fails`() = runTest {
        coEvery { restoreRepository.inspect(any()) } returns BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)

        viewModel.onFileSelected("uri")
        
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
}
