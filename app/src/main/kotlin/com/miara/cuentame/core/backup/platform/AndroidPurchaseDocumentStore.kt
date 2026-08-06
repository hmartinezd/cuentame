package com.miara.cuentame.core.backup.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miara.cuentame.core.backup.AttachmentFilenameSanitizer
import com.miara.cuentame.core.backup.BackupLimits
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.StoredPurchaseDocument
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPurchaseDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchaseDocumentStore {

    private val rootDir = File(context.filesDir, "attachments/purchases")

    override suspend fun importDocument(
        receiptId: PurchaseReceiptId,
        sourceUri: Uri
    ): StoredPurchaseDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: throw IllegalArgumentException("Could not determine MIME type")
        
        if (mimeType !in PurchaseDocumentStore.SUPPORTED_MIME_TYPES) {
            throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }

        val metadata = resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else -1L
                name to size
            } else null
        }
        
        val originalName = metadata?.first
        val reportedSize = metadata?.second ?: -1L

        if (reportedSize > BackupLimits.MAX_SINGLE_DOCUMENT_BYTES) {
            throw IllegalArgumentException("File too large: $reportedSize bytes")
        }

        val receiptDir = File(rootDir, receiptId.value).apply { if (!exists()) mkdirs() }
        
        // Sanitize and generate a stable but unique filename
        val safeName = generateSafeFilename(originalName, mimeType)
        val targetFile = File(receiptDir, safeName)
        val tempFile = File(receiptDir, "${safeName}.tmp")

        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalWritten = 0L
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        totalWritten += n
                        if (totalWritten > BackupLimits.MAX_SINGLE_DOCUMENT_BYTES) {
                            throw IllegalArgumentException("File exceeded size limit during copy")
                        }
                    }
                }
            } ?: throw IllegalStateException("Could not open source stream")

            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to commit imported file")
            }

            StoredPurchaseDocument(
                location = targetFile.toRelativePath(),
                displayName = originalName ?: safeName,
                mimeType = mimeType,
                sizeBytes = targetFile.length()
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    override suspend fun inspect(storedLocation: String): StoredPurchaseDocument? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, storedLocation)
        if (!file.exists() || !file.isChildOf(rootDir)) return@withContext null

        StoredPurchaseDocument(
            location = storedLocation,
            displayName = file.name, // Fallback to filename as we don't store original separately
            mimeType = inferMimeType(file),
            sizeBytes = file.length()
        )
    }

    override suspend fun delete(storedLocation: String) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, storedLocation)
            if (file.exists() && file.isChildOf(rootDir)) {
                file.delete()
            }
        }
    }

    override suspend fun open(storedLocation: String): InputStream = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, storedLocation)
        if (!file.exists() || !file.isChildOf(rootDir)) {
            throw java.io.FileNotFoundException("Stored document not found or access denied: $storedLocation")
        }
        file.inputStream()
    }

    private fun File.toRelativePath(): String {
        return this.absolutePath.removePrefix(context.filesDir.absolutePath).trimStart('/')
    }

    private fun File.isChildOf(parent: File): Boolean {
        var current: File? = this.canonicalFile
        val canonicalParent = parent.canonicalFile
        while (current != null) {
            if (current == canonicalParent) return true
            current = current.parentFile
        }
        return false
    }

    private fun generateSafeFilename(originalName: String?, mimeType: String): String {
        val sanitized = AttachmentFilenameSanitizer.sanitize(originalName)
        val timestamp = System.currentTimeMillis()
        val extension = when (mimeType) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ""
        }
        val nameWithoutExtension = sanitized.substringBeforeLast(".")
        // Ensure the final name is not too long for the sanitizer's limits if possible
        val base = if (nameWithoutExtension.length > 100) nameWithoutExtension.substring(0, 100) else nameWithoutExtension
        return "${base}_${timestamp}${extension}"
    }

    private fun inferMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
