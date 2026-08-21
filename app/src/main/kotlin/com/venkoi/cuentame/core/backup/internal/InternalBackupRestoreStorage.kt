package com.venkoi.cuentame.core.backup.internal

import android.content.Context
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalBackupRestoreStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir: File
        get() = File(context.filesDir, "backup_restore").apply { if (!exists()) mkdirs() }

    /**
     * @deprecated Not used by Backup and Restore v1.
     */
    fun getStagingDir(sessionId: String): File {
        return File(baseDir, "staging/$sessionId")
    }

    fun getRollbackDir(sessionId: String): File {
        return File(baseDir, "rollback/$sessionId")
    }

    fun getRollbackSnapshotFile(sessionId: String): File {
        return File(getRollbackDir(sessionId), "rollback_snapshot.json")
    }

    fun getFilesDir(): File = context.filesDir

    fun saveRollbackSnapshot(sessionId: String, json: String) {
        val file = getRollbackSnapshotFile(sessionId)
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val fos = atomicFile.startWrite()
        try {
            fos.write(json.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            throw e
        }
    }

    /**
     * @deprecated Not used by Backup and Restore v1.
     */
    fun getLiveAttachmentDir(): File {
        return File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
    }

    fun getJournalFile(): File {
        return File(baseDir, "restore_journal.json")
    }

    fun cleanupSession(sessionId: String) {
        File(baseDir, "staging/$sessionId").deleteRecursively()
        File(baseDir, "rollback/$sessionId").deleteRecursively()
    }

    fun cleanupSessionOrThrow(sessionId: String) {
        val staging = File(baseDir, "staging/$sessionId")
        val rollback = File(baseDir, "rollback/$sessionId")
        
        if (staging.exists() && !staging.deleteRecursively()) {
            throw java.io.IOException("Failed to cleanup staging session: $sessionId")
        }
        if (rollback.exists() && !rollback.deleteRecursively()) {
            throw java.io.IOException("Failed to cleanup rollback session: $sessionId")
        }
    }
}
