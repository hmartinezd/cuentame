package com.miara.cuentame.core.backup.internal

import android.content.Context
import com.miara.cuentame.core.database.dao.BackupDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseAttachmentCleanupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao
) {
    private val rootDir = File(context.filesDir, "attachments")

    /**
     * Deletes files in the attachments directory that are not referenced in the database.
     * Skips .tmp files which may belong to active operations.
     */
    suspend fun cleanupOrphans() = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) return@withContext

        val activePurchasePaths = backupDao.getAllPurchaseReceipts()
            .mapNotNull { it.attachmentPath }
            .toSet()
            
        val activeWastePaths = backupDao.getAllWasteEvents()
            .mapNotNull { it.attachmentPath }
            .toSet()
            
        val activePaths = activePurchasePaths + activeWastePaths

        fun cleanupRecursive(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    cleanupRecursive(child)
                    // Delete empty subdirectories (but not the root or direct category dirs)
                    if (child.listFiles()?.isEmpty() == true && dir != rootDir) {
                        child.delete()
                    }
                } else {
                    val relativePath = child.absolutePath.removePrefix(context.filesDir.absolutePath).trimStart('/')
                    if (relativePath !in activePaths && !child.name.endsWith(".tmp")) {
                        child.delete()
                    }
                }
            }
        }

        cleanupRecursive(rootDir)
    }
}
