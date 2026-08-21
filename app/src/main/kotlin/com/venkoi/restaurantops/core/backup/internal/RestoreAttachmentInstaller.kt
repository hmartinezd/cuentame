package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.BackupLimits
import com.venkoi.restaurantops.core.backup.PurchaseAttachmentLocation
import com.venkoi.restaurantops.core.backup.ChecksumProvider
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.model.backup.BackupManifest
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
        val target = File(rollbackDir, "attachments")
        
        // Always create the rollback attachments directory to indicate capture was attempted
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("Could not create rollback directory")
        }

        val inventory = mutableListOf<RollbackAttachmentMetadata>()
        var totalBytes = 0L

        if (liveDir.exists()) {
            val livePath = liveDir.canonicalFile.toPath()
            val filesToCapture = liveDir.walkTopDown().filter { it.isFile }.toList()
            
            if (filesToCapture.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
                throw IllegalStateException("Rollback exceeds attachment count limit")
            }

            for (file in filesToCapture) {
                val canonicalFile = file.canonicalFile
                if (!canonicalFile.toPath().startsWith(livePath)) {
                    throw IllegalStateException("Symlink traversal detected during capture: ${file.absolutePath}")
                }
                
                if (file.length() > BackupLimits.MAX_SINGLE_DOCUMENT_BYTES) {
                    throw IllegalStateException("File exceeds individual size limit: ${file.name}")
                }
                
                totalBytes += file.length()
                if (totalBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw IllegalStateException("Total rollback size exceeds limit")
                }

                val relativePath = livePath.relativize(canonicalFile.toPath()).toString()
                // Validate relative path segments using project standards
                relativePath.split(File.separator).forEach { PurchaseAttachmentLocation.validateSegment(it, "pathSegment") }

                val rollbackFile = File(target, relativePath)
                val parent = rollbackFile.parentFile ?: throw IllegalStateException("Invalid rollback path")
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create directory: ${parent.absolutePath}")
                }
                
                file.copyTo(rollbackFile, overwrite = true)
                
                val checksum = rollbackFile.inputStream().use { checksumProvider.calculateChecksum(it) }
                inventory.add(
                    RollbackAttachmentMetadata(
                        relativePath = relativePath,
                        sizeBytes = rollbackFile.length(),
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
        val liveDir = storage.getLiveAttachmentDir()
        
        // Before installing new files, we clean the live directory
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
                
                val targetFile = if (ref.recordType == "PURCHASE_RECEIPT") {
                    PurchaseAttachmentLocation.resolvePurchaseDocument(filesDir, relativePath)
                } else {
                    PurchaseAttachmentLocation.resolveAttachmentRootEntry(filesDir, relativePath)
                }
                
                val parent = targetFile.parentFile ?: throw IllegalStateException("Invalid target path")
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create directory: ${parent.absolutePath}")
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
        val rollbackSource = File(rollbackDir, "attachments")
        
        if (!rollbackSource.exists()) {
            throw IllegalStateException("Rollback source missing")
        }

        // 1. Move aside mutated live directory to a temporary path
        val tempLive = File(liveDir.parentFile, "live_attachments_rollback_tmp_${System.currentTimeMillis()}")
        if (liveDir.exists()) {
            if (!liveDir.renameTo(tempLive)) {
                liveDir.deleteRecursively()
            }
        }

        // 2. Copy rollback files to live (ensures source survives if verification fails)
        try {
            if (!rollbackSource.copyRecursively(liveDir, overwrite = true)) {
                throw IllegalStateException("Rollback copy failed")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Rollback copy failed: ${e.message}", e)
        } finally {
            if (tempLive.exists()) tempLive.deleteRecursively()
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
                
                val file = if (ref.recordType == "PURCHASE_RECEIPT") {
                    PurchaseAttachmentLocation.resolvePurchaseDocument(filesDir, relativePath)
                } else {
                    PurchaseAttachmentLocation.resolveAttachmentRootEntry(filesDir, relativePath)
                }

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
        val livePath = liveDir.canonicalFile.toPath()

        val actualFiles = if (liveDir.exists()) {
            liveDir.walkTopDown().filter { it.isFile }.associateBy {
                livePath.relativize(it.canonicalFile.toPath()).toString()
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
