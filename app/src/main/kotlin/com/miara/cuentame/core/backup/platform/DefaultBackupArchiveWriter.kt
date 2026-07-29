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

    override suspend fun write(
        outputStream: OutputStream,
        plan: BackupPlan
    ): BackupArchiveWriteResult {
        val precheck = validatePlanLimits(plan)
        if (precheck != null) return precheck

        val zos = ZipOutputStream(NonClosingOutputStream(outputStream))
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

        var currentTotalUncompressedBytes = 0L

        try {
            // 1. data/database.json
            writeZipEntry(zos, "data/database.json", plan.snapshotJson, plan)
            currentTotalUncompressedBytes += plan.snapshotJson.size

            // 2. preferences/settings.json
            writeZipEntry(zos, "preferences/settings.json", plan.preferencesJson, plan)
            currentTotalUncompressedBytes += plan.preferencesJson.size

            // 3. attachments
            for (att in plan.attachments) {
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
                    var entrySize = 0L
                    var n: Int
                    
                    while (stream.read(buffer).also { n = it } != -1) {
                        digest.update(buffer, 0, n)
                        zos.write(buffer, 0, n)
                        entrySize += n
                        currentTotalUncompressedBytes += n
                        
                        if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            return BackupArchiveWriteResult.Failure.LimitExceeded
                        }
                    }
                    zos.closeEntry()

                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    
                    if (entrySize != att.sizeBytes || checksum != att.checksumSha256) {
                        return BackupArchiveWriteResult.Failure.AttachmentChanged
                    }
                    
                    if (plan.expectedEntryChecksums[att.archivePath] != checksum) {
                        return BackupArchiveWriteResult.Failure.AttachmentChanged
                    }
                }
            }

            // 4. manifest.json
            writeZipEntry(zos, "manifest.json", plan.manifestJson, plan)
            currentTotalUncompressedBytes += plan.manifestJson.size

            // 5. checksums.json
            zos.putNextEntry(ZipEntry("checksums.json").apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
            plan.checksumsJson.writeTo(zos)
            zos.closeEntry()
            currentTotalUncompressedBytes += plan.checksumsJson.size

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

    private fun validatePlanLimits(plan: BackupPlan): BackupArchiveWriteResult.Failure? {
        if (plan.attachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (2 + plan.attachments.size + 2 > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded
        
        if (plan.snapshotJson.size > BackupLimits.MAX_DATABASE_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.manifestJson.size > BackupLimits.MAX_MANIFEST_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.checksumsJson.size > BackupLimits.MAX_CHECKSUMS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        val allNames = listOf("data/database.json", "preferences/settings.json", "manifest.json", "checksums.json") + 
                plan.attachments.map { it.archivePath }
        if (allNames.any { it.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES }) {
             return BackupArchiveWriteResult.Failure.LimitExceeded
        }

        val expectedKeys = setOf("data/database.json", "preferences/settings.json", "manifest.json") + 
                plan.attachments.map { it.archivePath }.toSet()
        if (plan.expectedEntryChecksums.keys != expectedKeys) {
             return BackupArchiveWriteResult.Failure.LimitExceeded
        }

        return null
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ImmutableBackupBytes, plan: BackupPlan) {
        val actualHash = bytes.sha256()
        if (plan.expectedEntryChecksums[name] != actualHash) {
             throw IllegalStateException("Inconsistent plan: checksum mismatch for $name")
        }
        
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        bytes.writeTo(zos)
        zos.closeEntry()
    }
}
