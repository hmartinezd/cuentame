package com.venkoi.restaurantops.core.backup.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.venkoi.restaurantops.core.backup.AttachmentFilenameSanitizer
import com.venkoi.restaurantops.core.backup.BackupLimits
import com.venkoi.restaurantops.core.backup.PurchaseAttachmentLocation
import com.venkoi.restaurantops.core.backup.api.PurchaseDocumentStore
import com.venkoi.restaurantops.core.backup.api.StoredPurchaseDocument
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPurchaseDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchaseDocumentStore {

    override suspend fun importDocument(
        receiptId: PurchaseReceiptId,
        sourceUri: Uri,
        displayNameOverride: String?
    ): StoredPurchaseDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        
        var mimeType = resolver.getType(sourceUri)
        if (mimeType == null && sourceUri.scheme == "file") {
            mimeType = inferMimeType(File(sourceUri.path ?: ""))
        }

        if (mimeType == null) {
            throw IllegalArgumentException("Could not determine MIME type")
        }
        
        if (mimeType !in PurchaseDocumentStore.SUPPORTED_MIME_TYPES) {
            throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }

        val metadata = if (sourceUri.scheme == "content") {
            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else -1L
                    name to size
                } else null
            }
        } else if (sourceUri.scheme == "file") {
            val file = File(sourceUri.path ?: "")
            file.name to file.length()
        } else null
        
        val originalName = displayNameOverride ?: metadata?.first
        val reportedSize = metadata?.second ?: -1L

        if (reportedSize > BackupLimits.MAX_SINGLE_DOCUMENT_BYTES) {
            throw IllegalArgumentException("File too large: $reportedSize bytes")
        }

        val receiptDir = PurchaseAttachmentLocation.resolvePurchaseDirectory(context.filesDir, receiptId)
        if (!receiptDir.exists() && !receiptDir.mkdirs()) {
            throw IllegalStateException("Could not create attachment directory")
        }
        
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

            // Content Validation
            try {
                when (mimeType) {
                    "application/pdf" -> validatePdf(tempFile)
                    "image/jpeg", "image/png", "image/webp" -> validateImage(tempFile, mimeType)
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Content validation failed: ${e.message}")
            }

            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to commit imported file")
            }

            val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(receiptId, safeName)

            StoredPurchaseDocument(
                location = relativePath,
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
        val file = try {
            PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, storedLocation)
        } catch (e: Exception) {
            return@withContext null
        }
        
        if (!file.exists()) return@withContext null

        StoredPurchaseDocument(
            location = storedLocation,
            displayName = file.name,
            mimeType = inferMimeType(file),
            sizeBytes = file.length()
        )
    }

    override suspend fun delete(storedLocation: String) {
        withContext(Dispatchers.IO) {
            val file = try {
                PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, storedLocation)
            } catch (e: Exception) {
                return@withContext
            }
            if (file.exists()) {
                val parent = file.parentFile
                if (!file.delete()) return@withContext
                
                // Safely remove receipt directory if now empty, but no higher
                if (parent != null && parent.name != "purchases" && parent.listFiles()?.isEmpty() == true) {
                    parent.delete()
                }
            }
        }
    }

    override suspend fun open(storedLocation: String): InputStream = withContext(Dispatchers.IO) {
        val file = PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, storedLocation)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Stored document not found: $storedLocation")
        }
        file.inputStream()
    }

    override suspend fun getFile(storedLocation: String): File = withContext(Dispatchers.IO) {
        val file = PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, storedLocation)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Stored document not found: $storedLocation")
        }
        file
    }

    private fun validatePdf(file: File) {
        if (file.length() == 0L) throw IllegalArgumentException("Empty PDF file")
        
        // Check signature
        file.inputStream().use { input ->
            val buffer = ByteArray(4)
            if (input.read(buffer) != 4 || String(buffer) != "%PDF") {
                throw IllegalArgumentException("Invalid PDF signature")
            }
        }

        // Try opening with renderer to verify it's readable and not password-protected
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) throw IllegalArgumentException("PDF has no pages")
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not open PDF: ${e.message}")
        }
    }

    private fun validateImage(file: File, reportedMimeType: String) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IllegalArgumentException("Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
        }
        val actualMimeType = options.outMimeType ?: throw IllegalArgumentException("Could not determine image type")
        
        // Normalize and compare
        if (!isMimeTypeMatch(actualMimeType, reportedMimeType)) {
            throw IllegalArgumentException("MIME type mismatch: actual $actualMimeType vs reported $reportedMimeType")
        }
    }

    private fun isMimeTypeMatch(actual: String, reported: String): Boolean {
        if (actual == reported) return true
        // Handle common aliases/subtypes if needed, e.g. image/jpg vs image/jpeg
        val normalizedActual = actual.replace("image/jpg", "image/jpeg")
        val normalizedReported = reported.replace("image/jpg", "image/jpeg")
        return normalizedActual == normalizedReported
    }

    private fun generateSafeFilename(originalName: String?, mimeType: String): String {
        val sanitized = AttachmentFilenameSanitizer.sanitize(originalName)
        val uuid = UUID.randomUUID().toString()
        val extension = when (mimeType) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ""
        }
        val nameWithoutExtension = sanitized.substringBeforeLast(".")
        val base = if (nameWithoutExtension.length > 50) nameWithoutExtension.substring(0, 50) else nameWithoutExtension
        return "${base}_${uuid}${extension}"
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
