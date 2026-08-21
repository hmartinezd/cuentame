package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.*
import com.venkoi.restaurantops.core.backup.fakes.FakeBackupDocumentStore
import com.venkoi.restaurantops.core.backup.platform.AndroidBackupRestoreRepository
import com.venkoi.restaurantops.core.model.backup.BackupRestoreFailure
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BackupRestoreRepositoryTest {

    private val documentStore = FakeBackupDocumentStore()
    private val archiveReader = mockk<BackupArchiveReader>()
    private val docUri = BackupDocumentUri("file:///test.zip")
    private lateinit var repository: AndroidBackupRestoreRepository

    @Before
    fun setup() {
        repository = AndroidBackupRestoreRepository(documentStore, archiveReader)
    }

    @Test
    fun `inspect successfully returns ready result and closes stream`() = runTest {
        val ready = BackupArchiveInspectionResult.Ready(mockk(), mockk(), com.venkoi.restaurantops.core.model.backup.BackupRestoreEligibility.Eligible)
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } returns ready
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isEqualTo(ready)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }

    @Test
    fun `inspect successfully returns ready result and closes stream exactly once`() = runTest {
        val ready = BackupArchiveInspectionResult.Ready(mockk(), mockk(), com.venkoi.restaurantops.core.model.backup.BackupRestoreEligibility.Eligible)
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } returns ready
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isEqualTo(ready)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }

    @Test
    fun `inspect returns source unavailable when document cannot be opened`() = runTest {
        documentStore.openForReadError = BackupDocumentOpenException(BackupDocumentOperation.READ)
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.SourceUnavailable)
        // Assert the expected open attempt according to fake contract
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        // Assert no successfully opened stream was closed (it failed to open)
        assertThat(documentStore.closeCountMap[docUri] ?: 0).isEqualTo(0)
    }

    @Test
    fun `inspect closes stream after reader failure`() = runTest {
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } returns BackupArchiveInspectionResult.Failure(BackupRestoreFailure.InvalidZip)
        
        val result = repository.inspect(docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.InvalidZip)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }

    @Test
    fun `inspect closes stream after reader exception`() = runTest {
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } throws RuntimeException("Reader crash")
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.GenericIo)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }

    @Test
    fun `inspect closes stream after cancellation and propagates`() = runTest {
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } throws CancellationException("User cancelled")
        
        try {
            repository.inspect(docUri)
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertThat(documentStore.openForReadCalls).containsExactly(docUri)
            assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
        }
    }

    @Test
    fun `inspect returns permission denied on security exception`() = runTest {
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } throws SecurityException("Denied")

        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.PermissionDenied)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }

    @Test
    fun `inspect returns generic io on unknown error`() = runTest {
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } throws IOException("Disk error")

        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.GenericIo)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
        assertThat(documentStore.closeCountMap[docUri]).isEqualTo(1)
    }
}
