package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.BackupLimits
import com.miara.cuentame.core.backup.api.*
import kotlinx.coroutines.CancellationException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBackupArchiveWriter @Inject constructor(
    private val attachmentSource: BackupAttachmentSource
) : BackupArchiveWriter {

    private val DETERMINISTIC_ZIP_TIMESTAMP = 0L
    private val FORMAT_V1_ATTACHMENT_ID = Regex("^[0-9a-f]{16}$")

    override suspend fun write(
        outputStream: OutputStream,
        plan: BackupPlan
    ): BackupArchiveWriteResult {
        // Pre-writing verification
        val precheck = prevalidatePlan(plan)
        if (precheck != null) return precheck

        val zos = ZipOutputStream(NonClosingOutputStream(outputStream))
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

        var currentTotalUncompressedBytes = 0L

        try {
            // 1. data/database.json
            writeEntry(zos, "data/database.json", plan.snapshotJson, plan)
            currentTotalUncompressedBytes += plan.snapshotJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 2. preferences/settings.json
            writeEntry(zos, "preferences/settings.json", plan.preferencesJson, plan)
            currentTotalUncompressedBytes += plan.preferencesJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 3. attachments
            for (att in plan.attachments) {
                // Secondary validation of attachment metadata
                if (!FORMAT_V1_ATTACHMENT_ID.matches(att.attachmentId)) return BackupArchiveWriteResult.Failure.AttachmentChanged
                if (att.sizeBytes < 0) return BackupArchiveWriteResult.Failure.AttachmentChanged
                if (att.archivePath.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

                val inputStream = try {
                    attachmentSource.open(att.sourceUri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return BackupArchiveWriteResult.Failure.AttachmentUnreadable
                }

                inputStream.use { stream ->
                    zos.putNextEntry(ZipEntry(att.archivePath).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
                    
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    var actualSize = 0L
                    var n: Int
                    
                    try {
                        while (stream.read(buffer).also { n = it } != -1) {
                            digest.update(buffer, 0, n)
                            zos.write(buffer, 0, n)
                            actualSize += n
                            currentTotalUncompressedBytes += n
                            
                            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                return BackupArchiveWriteResult.Failure.LimitExceeded
                            }
                        }
                    } finally {
                        zos.closeEntry()
                    }

                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    
                    if (actualSize != att.sizeBytes || actualHash != att.checksumSha256) {
                        return BackupArchiveWriteResult.Failure.AttachmentChanged
                    }
                    if (plan.expectedEntryChecksums[att.archivePath] != actualHash) {
                        return BackupArchiveWriteResult.Failure.AttachmentChanged
                    }
                }
            }

            // 4. manifest.json
            writeEntry(zos, "manifest.json", plan.manifestJson, plan)
            currentTotalUncompressedBytes += plan.manifestJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 5. checksums.json
            zos.putNextEntry(ZipEntry("checksums.json").apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
            try {
                plan.checksumsJson.writeTo(zos)
            } finally {
                zos.closeEntry()
            }
            currentTotalUncompressedBytes += plan.checksumsJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            zos.finish()
            zos.flush()
            return BackupArchiveWriteResult.Success

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BackupArchiveWriteResult.Failure.IoError(e)
        } finally {
            runCatching { zos.close() }
        }
    }

    private fun prevalidatePlan(plan: BackupPlan): BackupArchiveWriteResult.Failure? {
        val calculatedTotal = plan.snapshotJson.size.toLong() +
                plan.preferencesJson.size.toLong() +
                plan.attachments.sumOf { it.sizeBytes } +
                plan.manifestJson.size.toLong() +
                plan.checksumsJson.size.toLong()

        if (calculatedTotal != plan.totalUncompressedBytes) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (calculatedTotal > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Entry count check
        if (4 + plan.attachments.size > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded

        val expectedKeys = setOf("data/database.json", "preferences/settings.json", "manifest.json") + 
                plan.attachments.map { it.archivePath }.toSet()
        
        if (plan.expectedEntryChecksums.keys != expectedKeys) return BackupArchiveWriteResult.Failure.LimitExceeded

        return null
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, bytes: ImmutableBackupBytes, plan: BackupPlan) {
        val expectedHash = plan.expectedEntryChecksums[name] ?: throw IllegalStateException("Plan missing checksum for $name")
        val actualHash = bytes.sha256()
        if (expectedHash != actualHash) {
             throw IllegalStateException("Inconsistent plan: checksum mismatch for $name")
        }
        
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        try {
            bytes.writeTo(zos)
        } finally {
            zos.closeEntry()
        }
    }
}
