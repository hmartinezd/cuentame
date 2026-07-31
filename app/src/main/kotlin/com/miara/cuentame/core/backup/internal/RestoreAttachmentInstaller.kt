package com.miara.cuentame.core.backup.internal

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreAttachmentInstaller @Inject constructor(
    private val storage: InternalBackupRestoreStorage
) {
    /**
     * Moves current live attachments to a rollback location.
     */
    fun captureRollback(sessionId: String) {
        val liveDir = storage.getLiveAttachmentDir()
        val rollbackDir = storage.getRollbackDir(sessionId)
        
        if (liveDir.exists()) {
            val target = File(rollbackDir, "attachments")
            if (!liveDir.renameTo(target)) {
                // If rename fails (e.g. cross-filesystem), fallback to copy+delete
                liveDir.copyRecursively(target, overwrite = true)
                liveDir.deleteRecursively()
            }
        }
    }

    /**
     * Moves staged attachments to the live location.
     */
    fun installStaged(sessionId: String, stagedDir: File) {
        val liveDir = storage.getLiveAttachmentDir()
        
        // Ensure liveDir is clean (it should be if captureRollback was called)
        if (liveDir.exists()) {
            liveDir.deleteRecursively()
        }
        
        if (stagedDir.exists()) {
            if (!stagedDir.renameTo(liveDir)) {
                stagedDir.copyRecursively(liveDir, overwrite = true)
                stagedDir.deleteRecursively()
            }
        }
    }

    /**
     * Reverts from rollback location to live.
     */
    fun rollback(sessionId: String) {
        val liveDir = storage.getLiveAttachmentDir()
        val rollbackDir = storage.getRollbackDir(sessionId)
        val target = File(rollbackDir, "attachments")
        
        if (target.exists()) {
            if (liveDir.exists()) {
                liveDir.deleteRecursively()
            }
            if (!target.renameTo(liveDir)) {
                target.copyRecursively(liveDir, overwrite = true)
                target.deleteRecursively()
            }
        }
    }
}
