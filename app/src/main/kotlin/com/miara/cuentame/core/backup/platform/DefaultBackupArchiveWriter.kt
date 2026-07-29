package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.BackupChecksumException
import com.miara.cuentame.core.backup.BackupLimits
import com.miara.cuentame.core.backup.ChecksumParser
import com.miara.cuentame.core.backup.api.*
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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
        // 1. Pre-writing verification
        val precheck = prevalidatePlan(plan)
        if (precheck != null) return precheck

        val zos = ZipOutputStream(NonClosingOutputStream(outputStream))
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

        var currentTotalUncompressedBytes = 0L

        try {
            // 1. data/database.json
            writeZipEntry(zos, BackupFormatV1Contract.DATABASE_ENTRY, plan.snapshotJson, plan)
            currentTotalUncompressedBytes += plan.snapshotJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 2. preferences/settings.json
            writeZipEntry(zos, BackupFormatV1Contract.PREFERENCES_ENTRY, plan.preferencesJson, plan)
            currentTotalUncompressedBytes += plan.preferencesJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 3. attachments
            for (att in plan.attachments) {
                // Secondary validation of attachment metadata
                if (!BackupFormatV1Contract.isValidAttachmentId(att.attachmentId)) return BackupArchiveWriteResult.Failure.InvalidPlan
                if (att.sizeBytes < 0) return BackupArchiveWriteResult.Failure.InvalidPlan
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
                            if (actualSize > att.sizeBytes) {
                                return BackupArchiveWriteResult.Failure.AttachmentChanged
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
                        return BackupArchiveWriteResult.Failure.ChecksumInconsistency
                    }
                }
            }

            // 4. manifest.json
            writeZipEntry(zos, BackupFormatV1Contract.MANIFEST_ENTRY, plan.manifestJson, plan)
            currentTotalUncompressedBytes += plan.manifestJson.size
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

            // 5. checksums.json
            zos.putNextEntry(ZipEntry(BackupFormatV1Contract.CHECKSUMS_ENTRY).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
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
        } catch (e: BackupChecksumException) {
            return BackupArchiveWriteResult.Failure.ChecksumInconsistency
        } catch (e: IOException) {
            return BackupArchiveWriteResult.Failure.IoError(e)
        } catch (e: Exception) {
            return BackupArchiveWriteResult.Failure.IoError(IOException(e))
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

        if (calculatedTotal != plan.totalUncompressedBytes) return BackupArchiveWriteResult.Failure.InvalidPlan
        if (calculatedTotal > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Entry count check
        if (4 + plan.attachments.size > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded

        // JSON size limits
        if (plan.snapshotJson.size > BackupLimits.MAX_DATABASE_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.manifestJson.size > BackupLimits.MAX_MANIFEST_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.checksumsJson.size > BackupLimits.MAX_CHECKSUMS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Checksums validation
        val checksumsJsonStr = decodeStrictUtf8(plan.checksumsJson.copyForTest()) ?: return BackupArchiveWriteResult.Failure.InvalidPlan
        val parsedChecksums = ChecksumParser.parse(checksumsJsonStr).getOrElse { return BackupArchiveWriteResult.Failure.InvalidPlan }
        
        if (parsedChecksums != plan.expectedEntryChecksums) return BackupArchiveWriteResult.Failure.ChecksumInconsistency

        val expectedKeys = setOf(
            BackupFormatV1Contract.DATABASE_ENTRY,
            BackupFormatV1Contract.PREFERENCES_ENTRY,
            BackupFormatV1Contract.MANIFEST_ENTRY
        ) + plan.attachments.map { it.archivePath }.toSet()
        
        if (plan.expectedEntryChecksums.keys != expectedKeys) return BackupArchiveWriteResult.Failure.ChecksumInconsistency

        return null
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ImmutableBackupBytes, plan: BackupPlan) {
        val expectedHash = plan.expectedEntryChecksums[name] ?: throw BackupChecksumException("Plan missing checksum for $name")
        val actualHash = bytes.sha256()
        if (expectedHash != actualHash) {
             throw BackupChecksumException("Inconsistent plan: checksum mismatch for $name")
        }
        
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        var primaryFailure: Throwable? = null
        try {
            bytes.writeTo(zos)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            try {
                zos.closeEntry()
            } catch (closeError: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeError)
                } else {
                    throw closeError
                }
            }
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? {
        return try {
            val decoder = Charset.forName("UTF-8").newDecoder()
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            null
        }
    }
}
