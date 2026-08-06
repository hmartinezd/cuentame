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
        return "$ROOT_DIR/${receiptId.value}"
    }

    /**
     * Returns the relative file path for a specific attachment.
     */
    fun file(receiptId: PurchaseReceiptId, storageFilename: String): String {
        return "${directory(receiptId)}/$storageFilename"
    }

    /**
     * Safely resolves a stored relative path into a [File] under [filesDir].
     * Rejects path traversal attempts.
     */
    fun resolveUnderFilesDir(filesDir: File, storedLocation: String): File {
        val file = File(filesDir, storedLocation)
        
        // Canonicalize to prevent ".." traversal
        val canonicalFilesDir = filesDir.canonicalFile.path
        val canonicalFile = file.canonicalFile.path
        
        if (!canonicalFile.startsWith(canonicalFilesDir)) {
            throw IllegalArgumentException("Path traversal attempt rejected: $storedLocation")
        }
        
        return file
    }
}
