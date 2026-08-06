package com.miara.cuentame.core.backup

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import java.io.File

/**
 * Defines the canonical filesystem layout for purchase invoice attachments.
 * All paths are relative to the application's [Context.getFilesDir].
 */
object PurchaseAttachmentLocation {
    private const val ROOT_DIR = "attachments/purchases"

    /**
     * Returns the relative directory path for a specific purchase's attachments.
     */
    fun directory(receiptId: PurchaseReceiptId): String {
        validateSegment(receiptId.value)
        return "$ROOT_DIR/${receiptId.value}"
    }

    /**
     * Returns the relative file path for a specific attachment.
     */
    fun file(receiptId: PurchaseReceiptId, storageFilename: String): String {
        validateSegment(storageFilename)
        return "${directory(receiptId)}/$storageFilename"
    }

    /**
     * Safely resolves a purchase invoice document into a [File] under [filesDir].
     * Restricts resolution to the authorized purchase root.
     */
    fun resolvePurchaseDocument(
        filesDir: File,
        storedLocation: String
    ): File {
        if (!storedLocation.startsWith("$ROOT_DIR/")) {
            throw IllegalArgumentException("Location outside purchase root: $storedLocation")
        }
        storedLocation.split("/").forEach { validateSegment(it) }
        return resolveUnderFilesDir(filesDir, storedLocation)
    }

    /**
     * Safely resolves a general attachment entry (e.g. for backup/restore).
     */
    fun resolveAttachmentRootEntry(
        filesDir: File,
        storedLocation: String
    ): File {
        if (!storedLocation.startsWith("attachments/")) {
            throw IllegalArgumentException("Location outside attachments root: $storedLocation")
        }
        storedLocation.split("/").forEach { validateSegment(it) }
        return resolveUnderFilesDir(filesDir, storedLocation)
    }

    /**
     * Validates that a string is a safe filesystem path segment.
     */
    fun validateSegment(segment: String) {
        if (segment.isBlank()) throw IllegalArgumentException("Blank path segment")
        if (segment == "." || segment == "..") throw IllegalArgumentException("Traversal segment rejected")
        if (segment.contains("/") || segment.contains("\\") || segment.contains("\u0000")) {
            throw IllegalArgumentException("Illegal characters in path segment: $segment")
        }
        if (segment.any { it.isISOControl() }) {
            throw IllegalArgumentException("Control characters in path segment")
        }
    }

    /**
     * Safely resolves a stored relative path into a [File] under [filesDir].
     * Rejects path traversal attempts.
     */
    fun resolveUnderFilesDir(filesDir: File, storedLocation: String): File {
        val rootPath = filesDir.canonicalFile.toPath()
        val file = File(filesDir, storedLocation)
        val candidatePath = file.canonicalFile.toPath()
        
        if (!candidatePath.startsWith(rootPath)) {
            throw IllegalArgumentException("Path traversal attempt rejected: $storedLocation")
        }
        
        return file
    }
}
