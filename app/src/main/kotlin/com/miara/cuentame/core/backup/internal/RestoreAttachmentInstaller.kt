package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.ChecksumProvider
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.backup.BackupManifest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreAttachmentInstaller @Inject constructor(
    private val storage: InternalBackupRestoreStorage,
    private val checksumProvider: ChecksumProvider
) {
    /**
     * Copies current live attachments to a rollback location.
     * Does NOT remove live attachments to ensure they remain usable if mutation preparation fails.
     */
    fun captureRollback(sessionId: String) {
        val liveDir = storage.getLiveAttachmentDir()
        val rollbackDir = storage.getRollbackDir(sessionId)
        
        if (liveDir.exists()) {
            val target = File(rollbackDir, "attachments")
            liveDir.copyRecursively(target, overwrite = true)
        }
    }

    /**
     * Installs staged attachments to their canonical live locations based on the manifest.
     */
    fun installStaged(sessionId: String, stagedDir: File, manifest: BackupManifest) {
        val filesDir = storage.getFilesDir()
        
        // Before installing new files, we clean the live directory (now that rollback is safe)
        val liveDir = storage.getLiveAttachmentDir()
        if (liveDir.exists()) {
            liveDir.deleteRecursively()
        }

        for (att in manifest.attachments) {
            val stagedFile = File(stagedDir, "attachments/${att.attachmentId}/${att.displayName}")
            if (!stagedFile.exists()) continue

            for (ref in att.referencedBy) {
                val relativePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> PurchaseAttachmentLocation.file(PurchaseReceiptId(ref.recordId), att.displayName)
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> "attachments/other/${att.attachmentId}/${att.displayName}"
                }
                
                val targetFile = try {
                    PurchaseAttachmentLocation.resolveUnderFilesDir(filesDir, relativePath)
                } catch (e: Exception) {
                    continue
                }
                
                targetFile.parentFile?.mkdirs()
                stagedFile.copyTo(targetFile, overwrite = true)
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

    /**
     * Verifies that all attachments in the manifest are correctly installed.
     */
    fun verify(manifest: BackupManifest) {
        val filesDir = storage.getFilesDir()
        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                val relativePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> PurchaseAttachmentLocation.file(PurchaseReceiptId(ref.recordId), att.displayName)
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> "attachments/other/${att.attachmentId}/${att.displayName}"
                }
                
                val file = PurchaseAttachmentLocation.resolveUnderFilesDir(filesDir, relativePath)
                if (!file.exists()) {
                    throw IllegalStateException("Attachment missing: $relativePath")
                }
                if (file.length() != att.sizeBytes) {
                    throw IllegalStateException("Attachment size mismatch: $relativePath")
                }
                
                val checksum = file.inputStream().use { checksumProvider.calculateChecksum(it) }
                if (checksum != att.checksumSha256) {
                    throw IllegalStateException("Attachment checksum mismatch: $relativePath")
                }
            }
        }
    }
}
