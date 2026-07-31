package com.miara.cuentame.core.backup.internal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalBackupRestoreStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir = File(context.filesDir, "backup_restore")

    fun getStagingDir(sessionId: String): File {
        return File(baseDir, "staging/$sessionId").apply { mkdirs() }
    }

    fun getRollbackDir(sessionId: String): File {
        return File(baseDir, "rollback/$sessionId").apply { mkdirs() }
    }

    fun getRollbackSnapshotFile(sessionId: String): File {
        return File(getRollbackDir(sessionId), "database_rollback.json")
    }

    fun getLiveAttachmentDir(): File {
        return File(context.filesDir, "attachments").apply { mkdirs() }
    }

    fun getJournalFile(): File {
        return File(baseDir, "restore_journal.json")
    }

    fun cleanupSession(sessionId: String) {
        File(baseDir, "staging/$sessionId").deleteRecursively()
        File(baseDir, "rollback/$sessionId").deleteRecursively()
    }
}
