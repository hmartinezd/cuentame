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

        var primaryFailure: Throwable? = null
        var result: BackupArchiveWriteResult = BackupArchiveWriteResult.Failure.InvalidPlan // Default

        try {
            var currentTotalUncompressedBytes = 0L

            // 1. data/database.json
            writeZipEntry(zos, BackupFormatV1Contract.DATABASE_ENTRY, plan.snapshotJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.snapshotJson.size.toLong())
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) throw LimitExceededException()

            // 2. preferences/settings.json
            writeZipEntry(zos, BackupFormatV1Contract.PREFERENCES_ENTRY, plan.preferencesJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.preferencesJson.size.toLong())
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) throw LimitExceededException()

            // 3. attachments
            for (att in plan.attachments) {
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
                            
                            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw LimitExceededException()
                            }
                            if (actualSize > att.sizeBytes) {
                                throw AttachmentChangedException("Attachment grew during writing")
                            }
                        }

                        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                        if (actualSize != att.sizeBytes || actualHash != att.checksumSha256) {
                            throw AttachmentChangedException("Attachment mismatch: size or hash changed")
                        }
                    }
                }
            }

            // 4. manifest.json
            writeZipEntry(zos, BackupFormatV1Contract.MANIFEST_ENTRY, plan.manifestJson, plan)
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.manifestJson.size.toLong())
            if (currentTotalUncompressedBytes > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) throw LimitExceededException()

            // 5. checksums.json
            writeZipEntryInternal(zos, BackupFormatV1Contract.CHECKSUMS_ENTRY) {
                plan.checksumsJson.writeTo(zos)
            }
            currentTotalUncompressedBytes = BackupByteMath.addExact(currentTotalUncompressedBytes, plan.checksumsJson.size.toLong())
            
            require(currentTotalUncompressedBytes == plan.totalUncompressedBytes) { "Total byte count mismatch" }

            zos.finish()
            zos.flush()
            result = BackupArchiveWriteResult.Success

        } catch (e: CancellationException) {
            primaryFailure = e
            throw e
        } catch (e: LimitExceededException) {
            result = BackupArchiveWriteResult.Failure.LimitExceeded
        } catch (e: AttachmentChangedException) {
            result = BackupArchiveWriteResult.Failure.AttachmentChanged
        } catch (e: AttachmentUnreadableException) {
            result = BackupArchiveWriteResult.Failure.AttachmentUnreadable
        } catch (e: BackupChecksumException) {
            result = BackupArchiveWriteResult.Failure.ChecksumInconsistency
        } catch (e: IOException) {
            result = BackupArchiveWriteResult.Failure.IoError(e)
        } catch (e: Exception) {
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
        if (calculatedTotal > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Entry count check
        if (4 + plan.attachments.size > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) return BackupArchiveWriteResult.Failure.LimitExceeded

        // Checksums validation
        val checksumsJsonStr = decodeStrictUtf8(plan.checksumsJson.copyForTest()) ?: return BackupArchiveWriteResult.Failure.InvalidPlan
        val parsedChecksums = ChecksumParser.parse(checksumsJsonStr).getOrElse { return BackupArchiveWriteResult.Failure.InvalidPlan }
        
        if (parsedChecksums != plan.expectedEntryChecksums) return BackupArchiveWriteResult.Failure.ChecksumInconsistency

        return null
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ImmutableBackupBytes, plan: BackupPlan) {
        val expectedHash = plan.expectedEntryChecksums[name] ?: throw BackupChecksumException("Plan missing checksum for $name")
        val actualHash = bytes.sha256()
        if (expectedHash != actualHash) {
             throw BackupChecksumException("Inconsistent plan: checksum mismatch for $name")
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
    private class LimitExceededException : IOException("Archive limit exceeded during write")
}
