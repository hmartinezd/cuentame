package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.fakes.FakeBackupDocumentStore
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.backup.internal.BackupCleanupOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackupCleanupCoordinatorTest {

    private val documentStore = FakeBackupDocumentStore()
    private val coordinator = BackupCleanupCoordinator(documentStore)
    private val uri = BackupDocumentUri("content://backup")

    @Test
    fun `returns Deleted when delete succeeds`() = runTest {
        documentStore.deleteResult = true
        val result = coordinator.cleanup(uri)
        assertThat(result).isEqualTo(BackupCleanupOutcome.Deleted)
        assertThat(documentStore.deleteCalls).hasSize(1)
    }

    @Test
    fun `returns Truncated when delete fails but truncate succeeds`() = runTest {
        documentStore.deleteResult = false
        documentStore.truncateResult = true
        val result = coordinator.cleanup(uri)
        assertThat(result).isEqualTo(BackupCleanupOutcome.Truncated)
        assertThat(documentStore.deleteCalls).hasSize(1)
        assertThat(documentStore.truncateCalls).hasSize(1)
    }

    @Test
    fun `returns Failed when both fail`() = runTest {
        documentStore.deleteResult = false
        documentStore.truncateResult = false
        val result = coordinator.cleanup(uri)
        assertThat(result).isEqualTo(BackupCleanupOutcome.Failed)
    }

    @Test
    fun `returns Failed when delete throws`() = runTest {
        val store = object : FakeBackupDocumentStore() {
            override suspend fun delete(document: BackupDocumentUri): Boolean {
                throw Exception("Disk error")
            }
            override suspend fun truncate(document: BackupDocumentUri): Boolean = false
        }
        val coord = BackupCleanupCoordinator(store)
        val result = coord.cleanup(uri)
        assertThat(result).isEqualTo(BackupCleanupOutcome.Failed)
    }
}
