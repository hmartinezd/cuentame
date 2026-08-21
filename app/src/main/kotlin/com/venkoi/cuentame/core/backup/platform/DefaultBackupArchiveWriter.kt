package com.venkoi.cuentame.core.backup.platform

import com.venkoi.cuentame.core.backup.ArchiveEntryValidator
import com.venkoi.cuentame.core.backup.AttachmentFilenameSanitizer
import com.venkoi.cuentame.core.backup.BackupLimits
import com.venkoi.cuentame.core.backup.ChecksumParser
import com.venkoi.cuentame.core.backup.api.*
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
    private val attachmentSource: BackupAttachmentSource,
    private val writeLimits: BackupWriteLimits = BackupWriteLimits()
) : BackupArchiveWriter {

    private val DETERMINISTIC_ZIP_TIMESTAMP = 0L

    override suspend fun write(
        outputStream: OutputStream,
        plan: BackupPlan
    ): BackupArchiveWriteResult {
        // 1. Writer prevalidation before opening ZipOutputStream
        val precheck = prevalidatePlan(plan)
        if (precheck != null) return precheck

        val zos = ZipOutputStream(NonClosingOutputStream(outputStream))
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

        var primaryFailure: Throwable? = null
        var result: BackupArchiveWriteResult = BackupArchiveWriteResult.Failure.InvalidPlan

        try {
            var currentTotalUncompressedBytes = 0L

            // 1. data/database.json
            writeZipEntry(zos, BackupFormatV1Contract.DATABASE_ENTRY, plan.snapshotJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.snapshotJson.size.toLong())
            if (currentTotalUncompressedBytes > writeLimits.maxTotalUncompressedBytes) throw LimitExceededException()

            // 2. preferences/settings.json
            writeZipEntry(zos, BackupFormatV1Contract.PREFERENCES_ENTRY, plan.preferencesJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.preferencesJson.size.toLong())
            if (currentTotalUncompressedBytes > writeLimits.maxTotalUncompressedBytes) throw LimitExceededException()

            // 3. attachments
            for (att in plan.attachments) {
                val expectedArchiveHash = plan.expectedEntryChecksums[att.archivePath]
                    ?: throw ChecksumInconsistencyException("Missing expected checksum for attachment ${att.archivePath}")

                if (att.checksumSha256 != expectedArchiveHash) {
                    throw ChecksumInconsistencyException("Checksum mismatch between attachment and expected map for ${att.archivePath}")
                }

                val inputStream = try {
                    attachmentSource.open(att.sourceUri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw AttachmentUnreadableException()
                }

                inputStream.use { stream ->
                    writeZipEntryInternal(zos, att.archivePath) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(8192)
                        var actualSize = 0L
                        var n: Int
                        
                        while (stream.read(buffer).also { n = it } != -1) {
                            digest.update(buffer, 0, n)
                            zos.write(buffer, 0, n)
                            actualSize = BackupByteMath.addExact(actualSize, n.toLong())
                            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, n.toLong())
                            
                            if (currentTotalUncompressedBytes > writeLimits.maxTotalUncompressedBytes) {
                                throw LimitExceededException()
                            }
                            if (actualSize > att.sizeBytes) {
                                throw AttachmentChangedException("Attachment grew during writing")
                            }
                        }

                        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                        if (actualSize != att.sizeBytes || actualHash != att.checksumSha256 || actualHash != expectedArchiveHash) {
                            throw AttachmentChangedException("Attachment mismatch: size or hash changed during writing")
                        }
                    }
                }
            }

            // 4. manifest.json
            writeZipEntry(zos, BackupFormatV1Contract.MANIFEST_ENTRY, plan.manifestJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.manifestJson.size.toLong())
            if (currentTotalUncompressedBytes > writeLimits.maxTotalUncompressedBytes) throw LimitExceededException()

            // 5. checksums.json
            writeZipEntryInternal(zos, BackupFormatV1Contract.CHECKSUMS_ENTRY) {
                plan.checksumsJson.writeTo(zos)
            }
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.checksumsJson.size.toLong())
            
            if (currentTotalUncompressedBytes != plan.totalUncompressedBytes) {
                result = BackupArchiveWriteResult.Failure.InvalidPlan
                return result
            }

            zos.finish()
            zos.flush()
            result = BackupArchiveWriteResult.Success

        } catch (e: CancellationException) {
            primaryFailure = e
            throw e
        } catch (e: BackupSizeOverflowException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.LimitExceeded
        } catch (e: LimitExceededException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.LimitExceeded
        } catch (e: AttachmentChangedException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.AttachmentChanged
        } catch (e: AttachmentUnreadableException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.AttachmentUnreadable
        } catch (e: ChecksumInconsistencyException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.ChecksumInconsistency
        } catch (e: IOException) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.IoError(e)
        } catch (e: Exception) {
            primaryFailure = e
            result = BackupArchiveWriteResult.Failure.IoError(IOException(e))
        } finally {
            try {
                zos.close()
            } catch (closeError: Throwable) {
                if (closeError.isFatal()) throw closeError
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeError)
                } else if (result is BackupArchiveWriteResult.Success) {
                    result = BackupArchiveWriteResult.Failure.IoError(IOException("Failed to close ZIP stream", closeError))
                }
            }
        }
        return result
    }

    private fun prevalidatePlan(plan: BackupPlan): BackupArchiveWriteResult.Failure? {
        // Individual JSON limit checks
        if (plan.snapshotJson.size > BackupLimits.MAX_DATABASE_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.manifestJson.size > BackupLimits.MAX_MANIFEST_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (plan.checksumsJson.size > BackupLimits.MAX_CHECKSUMS_JSON_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Count limits
        if (plan.attachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded
        if (4 + plan.attachments.size > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Entry-name limits and attachment invariant checks
        val seenIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()

        for (att in plan.attachments) {
            if (!seenIds.add(att.attachmentId)) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (!seenPaths.add(att.archivePath)) return BackupArchiveWriteResult.Failure.InvalidPlan

            if (!BackupFormatV1Contract.isValidAttachmentId(att.attachmentId)) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (att.displayName.isBlank() || !AttachmentFilenameSanitizer.isValid(att.displayName)) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (!ArchiveEntryValidator.isSafe(att.archivePath)) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (att.archivePath != BackupFormatV1Contract.attachmentArchivePath(att.attachmentId, att.displayName)) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (att.archivePath.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded
            if (att.sizeBytes < 0) return BackupArchiveWriteResult.Failure.InvalidPlan
            if (!BackupFormatV1Contract.isValidChecksum(att.checksumSha256)) return BackupArchiveWriteResult.Failure.InvalidPlan

            val refKeys = mutableSetOf<AttachmentReferenceKey>()
            for (ref in att.references) {
                if (ref.recordId.isBlank() || ref.recordType !in BackupFormatV1Contract.SUPPORTED_ATTACHMENT_RECORD_TYPES) {
                    return BackupArchiveWriteResult.Failure.InvalidPlan
                }
                val key = AttachmentReferenceKey(att.attachmentId, ref.recordType, ref.recordId)
                if (!refKeys.add(key)) return BackupArchiveWriteResult.Failure.InvalidPlan
            }
        }

        // Expected checksum key set & parser prevalidation
        val expectedKeys = setOf(
            BackupFormatV1Contract.DATABASE_ENTRY,
            BackupFormatV1Contract.PREFERENCES_ENTRY,
            BackupFormatV1Contract.MANIFEST_ENTRY
        ) + seenPaths

        if (plan.expectedEntryChecksums.keys != expectedKeys) return BackupArchiveWriteResult.Failure.ChecksumInconsistency

        val checksumsJsonStr = decodeStrictUtf8(plan.checksumsJson.copyForTest()) ?: return BackupArchiveWriteResult.Failure.InvalidPlan
        val parsedChecksums = ChecksumParser.parse(checksumsJsonStr).getOrElse { return BackupArchiveWriteResult.Failure.InvalidPlan }
        
        if (parsedChecksums != plan.expectedEntryChecksums) return BackupArchiveWriteResult.Failure.ChecksumInconsistency

        // Payload hash prevalidation
        if (plan.expectedEntryChecksums[BackupFormatV1Contract.DATABASE_ENTRY] != plan.snapshotJson.sha256()) {
            return BackupArchiveWriteResult.Failure.ChecksumInconsistency
        }
        if (plan.expectedEntryChecksums[BackupFormatV1Contract.PREFERENCES_ENTRY] != plan.preferencesJson.sha256()) {
            return BackupArchiveWriteResult.Failure.ChecksumInconsistency
        }
        if (plan.expectedEntryChecksums[BackupFormatV1Contract.MANIFEST_ENTRY] != plan.manifestJson.sha256()) {
            return BackupArchiveWriteResult.Failure.ChecksumInconsistency
        }

        // Overflow-safe total bytes calculation
        val calculatedTotal = try {
            var total = 0L
            total = BackupByteMath.addExact(total, plan.snapshotJson.size.toLong())
            total = BackupByteMath.addExact(total, plan.preferencesJson.size.toLong())
            plan.attachments.forEach { total = BackupByteMath.addExact(total, it.sizeBytes) }
            total = BackupByteMath.addExact(total, plan.manifestJson.size.toLong())
            total = BackupByteMath.addExact(total, plan.checksumsJson.size.toLong())
            total
        } catch (e: BackupSizeOverflowException) {
            return BackupArchiveWriteResult.Failure.LimitExceeded
        }

        if (calculatedTotal != plan.totalUncompressedBytes) return BackupArchiveWriteResult.Failure.InvalidPlan
        if (calculatedTotal > writeLimits.maxTotalUncompressedBytes) return BackupArchiveWriteResult.Failure.LimitExceeded

        return null
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ImmutableBackupBytes, plan: BackupPlan) {
        val expectedHash = plan.expectedEntryChecksums[name] ?: throw ChecksumInconsistencyException("Plan missing checksum for $name")
        val actualHash = bytes.sha256()
        if (expectedHash != actualHash) {
            throw ChecksumInconsistencyException("Inconsistent plan: checksum mismatch for $name")
        }
        writeZipEntryInternal(zos, name) {
            bytes.writeTo(zos)
        }
    }

    private fun writeZipEntryInternal(zip: ZipOutputStream, name: String, writeBlock: () -> Unit) {
        zip.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        var entryFailure: Throwable? = null
        try {
            writeBlock()
        } catch (error: Throwable) {
            entryFailure = error
            throw error
        } finally {
            try {
                zip.closeEntry()
            } catch (closeError: Throwable) {
                if (closeError.isFatal()) throw closeError
                if (entryFailure != null) {
                    entryFailure.addSuppressed(closeError)
                } else {
                    throw closeError
                }
            }
        }
    }

    private fun Throwable.isFatal(): Boolean =
        this is VirtualMachineError || this is ThreadDeath || this is LinkageError

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

    private class AttachmentChangedException(message: String) : IOException(message)
    private class AttachmentUnreadableException : IOException("Attachment became unreadable during write")
    private class ChecksumInconsistencyException(message: String) : IOException(message)
    private class LimitExceededException : IOException("Archive limit exceeded during write")
}
