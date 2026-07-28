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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @Test
    fun `initial state is Idle`() = runTest {
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Idle)
    }

    @Test
    fun `onCreateBackupRequested emits LaunchFilePicker and transitions to WaitingForDestination`() = runTest {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        every { timeProvider.now() } returns now
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)

        viewModel.events.test {
            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as BackupUiEvent.LaunchFilePicker
            assertThat(event.suggestedName).contains("My_Rest")
            assertThat(event.suggestedName).contains("2026-01-01")

            assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)
        }
    }

    @Test
    fun `onFileSelected transitions through Creating and Validating to Success`() = runTest {
        val manifest = mockk<BackupManifest>()
        val flow = flow {
            emit(BackupOperationStatus.Creating)
            emit(BackupOperationStatus.Validating)
            emit(BackupOperationStatus.Success(manifest))
        }
        every { backupRepository.createBackup(any()) } returns flow
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(BackupUiState.Idle)

            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BackupUiState.WaitingForDestination)

            viewModel.onFileSelected("uri")

            assertThat(awaitItem()).isEqualTo(BackupUiState.Creating)
            assertThat(awaitItem()).isEqualTo(BackupUiState.Validating)
            val success = awaitItem() as BackupUiState.Success
            assertThat(success.manifest).isEqualTo(manifest)
        }
    }

    @Test
    fun `onFileSelected atomically transitions state to Creating and ignores duplicate calls`() = runTest {
        val flow = flow {
            emit(BackupOperationStatus.Creating)
            kotlinx.coroutines.delay(1000)
            emit(BackupOperationStatus.Success(mockk()))
        }
        every { backupRepository.createBackup(any()) } returns flow
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()

        // First call
        viewModel.onFileSelected("uri-1")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating)

        // Duplicate call while Creating should be ignored
        viewModel.onFileSelected("uri-2")
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Creating)
    }

    @Test
    fun `onPickerCancelled transitions to Cancelled`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.onCreateBackupRequested()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPickerCancelled()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.Cancelled)
    }

    @Test
    fun `onFileSelected handles Error`() = runTest {
        every { backupRepository.createBackup(any()) } returns flowOf(
            BackupOperationStatus.Creating,
            BackupOperationStatus.Error(BackupResult.Error.PermissionDenied)
        )
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)
        every { timeProvider.now() } returns Instant.EPOCH

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // WaitingForDestination

            viewModel.onFileSelected("uri")
            awaitItem() // Creating
            assertThat(awaitItem()).isEqualTo(BackupUiState.Error(BackupResult.Error.PermissionDenied))
        }
    }

    @Test
    fun `duplicate onCreateBackupRequested are ignored while waiting`() = runTest {
        every { timeProvider.now() } returns Instant.EPOCH
        coEvery { restaurantRepository.getRestaurant() } returns Restaurant(RestaurantId("rest-1"), "My Rest", "USD", "en", Instant.EPOCH, Instant.EPOCH)

        viewModel.events.test {
            viewModel.onCreateBackupRequested()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isInstanceOf(BackupUiEvent.LaunchFilePicker::class.java)
            assertThat(viewModel.uiState.value).isEqualTo(BackupUiState.WaitingForDestination)

            // Second call should be ignored
            viewModel.onCreateBackupRequested()
            expectNoEvents()
        }
    }
}
