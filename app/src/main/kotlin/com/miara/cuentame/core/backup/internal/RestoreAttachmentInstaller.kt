package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.ChecksumProvider
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.backup.BackupManifest
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RollbackAttachmentMetadata(
    val relativePath: String,
    val sizeBytes: Long,
    val checksumSha256: String
)

@Singleton
class RestoreAttachmentInstaller @Inject constructor(
    private val storage: InternalBackupRestoreStorage,
    private val checksumProvider: ChecksumProvider
) {
    /**
     * Copies current live attachments to a rollback location and returns an inventory.
     * Does NOT remove live attachments to ensure they remain usable if mutation preparation fails.
     */
    fun captureRollback(sessionId: String): List<RollbackAttachmentMetadata> {
        val liveDir = storage.getLiveAttachmentDir()
        val rollbackDir = storage.getRollbackDir(sessionId)
        val inventory = mutableListOf<RollbackAttachmentMetadata>()
        
        if (liveDir.exists()) {
            val target = File(rollbackDir, "attachments")
            liveDir.copyRecursively(target, overwrite = true)
            
            target.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = target.toPath().relativize(file.toPath()).toString()
                val checksum = file.inputStream().use { checksumProvider.calculateChecksum(it) }
                inventory.add(
                    RollbackAttachmentMetadata(
                        relativePath = relativePath,
                        sizeBytes = file.length(),
                        checksumSha256 = checksum
                    )
                )
            }
        }
        return inventory
    }

    /**
     * Installs staged attachments to their canonical live locations based on the manifest.
     */
    fun installStaged(sessionId: String, stagedDir: File, manifest: BackupManifest) {
        val filesDir = storage.getFilesDir()
        
        // Before installing new files, we clean the live directory (now that rollback is safe)
        val liveDir = storage.getLiveAttachmentDir()
        if (liveDir.exists()) {
            if (!liveDir.deleteRecursively()) {
                throw IllegalStateException("Could not clean live attachment directory")
            }
        }

        for (att in manifest.attachments) {
            val stagedFile = File(stagedDir, "attachments/${att.attachmentId}/${att.displayName}")
            if (!stagedFile.exists()) {
                throw IllegalStateException("Staged attachment missing: ${att.attachmentId}/${att.displayName}")
            }

            for (ref in att.referencedBy) {
                val relativePath = when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> PurchaseAttachmentLocation.file(PurchaseReceiptId(ref.recordId), att.displayName)
                    "WASTE_EVENT" -> "attachments/waste/${ref.recordId}/${att.displayName}"
                    else -> throw IllegalArgumentException("Unsupported record type: ${ref.recordType}")
                }
                
                val targetFile = PurchaseAttachmentLocation.resolveUnderFilesDir(filesDir, relativePath)
                
                if (!targetFile.parentFile!!.exists() && !targetFile.parentFile!!.mkdirs()) {
                    throw IllegalStateException("Could not create directory: ${targetFile.parentFile}")
                }
                
                stagedFile.copyTo(targetFile, overwrite = true)
                
                if (targetFile.length() != att.sizeBytes) {
                    throw IllegalStateException("Copy failed: size mismatch for $relativePath")
                }
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
                if (!liveDir.deleteRecursively()) {
                    throw IllegalStateException("Could not delete live dir during rollback")
                }
            }
            if (!target.renameTo(liveDir)) {
                if (!target.copyRecursively(liveDir, overwrite = true)) {
                    throw IllegalStateException("Could not copy rollback files to live")
                }
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
                    else -> throw IllegalArgumentException("Unsupported record type: ${ref.recordType}")
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

    /**
     * Verifies that the live attachment tree matches the expected inventory.
     */
    fun verifyInventory(expected: List<RollbackAttachmentMetadata>) {
        val liveDir = storage.getLiveAttachmentDir()
        val livePath = liveDir.toPath()

        val actualFiles = if (liveDir.exists()) {
            liveDir.walkTopDown().filter { it.isFile }.associateBy {
                livePath.relativize(it.toPath()).toString()
            }
        } else emptyMap()

        if (actualFiles.size != expected.size) {
            throw IllegalStateException("Attachment count mismatch: expected ${expected.size}, got ${actualFiles.size}")
        }

        for (meta in expected) {
            val file = actualFiles[meta.relativePath] ?: throw IllegalStateException("Missing expected file: ${meta.relativePath}")
            
            if (file.length() != meta.sizeBytes) {
                throw IllegalStateException("Size mismatch for ${meta.relativePath}")
            }
            
            val checksum = file.inputStream().use { checksumProvider.calculateChecksum(it) }
            if (checksum != meta.checksumSha256) {
                throw IllegalStateException("Checksum mismatch for ${meta.relativePath}")
            }
        }
    }
}
