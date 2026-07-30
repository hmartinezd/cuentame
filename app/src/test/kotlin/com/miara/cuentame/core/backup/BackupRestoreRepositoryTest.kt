package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.FakeBackupDocumentStore
import com.miara.cuentame.core.backup.platform.AndroidBackupRestoreRepository
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import io.mockk.coEvery
import io.mockk.mockk
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
        val ready = BackupArchiveInspectionResult.Ready(mockk(), mockk())
        documentStore.storage[docUri] = "data".toByteArray()
        coEvery { archiveReader.inspect(any(), any()) } returns ready
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isEqualTo(ready)
        assertThat(documentStore.openForReadCalls).containsExactly(docUri)
    }

    @Test
    fun `inspect returns source unavailable when document cannot be opened`() = runTest {
        documentStore.openForReadError = BackupDocumentOpenException(BackupDocumentOperation.READ)
        
        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.SourceUnavailable)
    }

    @Test
    fun `inspect returns permission denied on security exception`() = runTest {
        coEvery { archiveReader.inspect(any(), any()) } throws SecurityException("Denied")
        documentStore.storage[docUri] = "data".toByteArray()

        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.PermissionDenied)
    }

    @Test
    fun `inspect returns generic io on unknown error`() = runTest {
        coEvery { archiveReader.inspect(any(), any()) } throws IOException("Disk error")
        documentStore.storage[docUri] = "data".toByteArray()

        val result = repository.inspect(docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.GenericIo)
    }
}
