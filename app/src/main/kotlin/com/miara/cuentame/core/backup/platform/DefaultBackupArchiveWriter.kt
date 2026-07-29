package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.BackupLimits
import com.miara.cuentame.core.backup.api.BackupArchiveWriteResult
import com.miara.cuentame.core.backup.api.BackupArchiveWriter
import com.miara.cuentame.core.backup.api.BackupAttachmentSource
import com.miara.cuentame.core.backup.api.BackupPlan
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
        val zos = ZipOutputStream(outputStream)
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

        var currentTotalUncompressedBytes = 0L

        try {
            // 1. data/database.json
            writeZipEntry(zos, "data/database.json", plan.snapshotJson)
            currentTotalUncompressedBytes += plan.snapshotJson.size

            // 2. preferences/settings.json
            writeZipEntry(zos, "preferences/settings.json", plan.preferencesJson)
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
                }
            }

            // 4. manifest.json
            writeZipEntry(zos, "manifest.json", plan.manifestJson)
            currentTotalUncompressedBytes += plan.manifestJson.size

            // 5. checksums.json
            writeZipEntry(zos, "checksums.json", plan.checksumsJson)
            currentTotalUncompressedBytes += plan.checksumsJson.size

            zos.finish()
            return BackupArchiveWriteResult.Success

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BackupArchiveWriteResult.Failure.IoError(e)
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: ByteArray) {
        zos.putNextEntry(ZipEntry(name).apply { time = DETERMINISTIC_ZIP_TIMESTAMP })
        zos.write(content)
        zos.closeEntry()
    }
}
