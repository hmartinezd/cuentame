package com.venkoi.restaurantops.core.backup

import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import java.io.File

/**
 * Defines the canonical filesystem layout for purchase invoice attachments.
 * All paths are relative to the application's [Context.getFilesDir].
 */
object PurchaseAttachmentLocation {
    private const val ATTACHMENTS_ROOT = "attachments"
    private const val PURCHASES_SUBDIR = "purchases"
    private const val ROOT_DIR = "$ATTACHMENTS_ROOT/$PURCHASES_SUBDIR"

    /**
     * Builds the relative location string for a purchase document.
     * Validates that both the receiptId and storageFilename are safe path segments.
     * Format: attachments/purchases/<receiptId>/<storageFilename>
     */
    fun buildRelativeLocation(
        receiptId: PurchaseReceiptId,
        storageFilename: String
    ): String {
        validateSegment(receiptId.value, "receiptId")
        validateSegment(storageFilename, "storageFilename")
        return "$ROOT_DIR/${receiptId.value}/$storageFilename"
    }

    /**
     * Legacy-compatible helper for directory path.
     */
    fun directory(receiptId: PurchaseReceiptId): String {
        validateSegment(receiptId.value, "receiptId")
        return "$ROOT_DIR/${receiptId.value}"
    }

    /**
     * Legacy-compatible helper for file path.
     */
    fun file(receiptId: PurchaseReceiptId, storageFilename: String): String {
        return buildRelativeLocation(receiptId, storageFilename)
    }

    /**
     * Safely resolves a purchase invoice document into a [File] under [filesDir].
     * Restricts resolution to the authorized purchase root and enforces exactly four segments.
     */
    fun resolvePurchaseDocument(
        filesDir: File,
        storedLocation: String
    ): File {
        val segments = storedLocation.split("/")
        if (segments.size != 4) {
            throw IllegalArgumentException("Malformed purchase location: $storedLocation. Expected 4 segments.")
        }
        if (segments[0] != ATTACHMENTS_ROOT || segments[1] != PURCHASES_SUBDIR) {
            throw IllegalArgumentException("Location outside purchase root: $storedLocation")
        }
        
        segments.forEachIndexed { index, segment ->
            val fieldName = when(index) {
                2 -> "receiptId"
                3 -> "storageFilename"
                else -> "pathPart"
            }
            validateSegment(segment, fieldName)
        }

        return resolveUnderPurchaseRoot(filesDir, storedLocation)
    }

    /**
     * Safely resolves a purchase directory for a specific receipt.
     */
    fun resolvePurchaseDirectory(
        filesDir: File,
        receiptId: PurchaseReceiptId
    ): File {
        validateSegment(receiptId.value, "receiptId")
        val relativePath = "$ROOT_DIR/${receiptId.value}"
        return resolveUnderPurchaseRoot(filesDir, relativePath)
    }

    /**
     * Safely resolves a general attachment entry (e.g. for waste or backup).
     */
    fun resolveAttachmentRootEntry(
        filesDir: File,
        storedLocation: String
    ): File {
        if (!storedLocation.startsWith("$ATTACHMENTS_ROOT/")) {
            throw IllegalArgumentException("Location outside attachments root: $storedLocation")
        }
        storedLocation.split("/").forEach { validateSegment(it, "pathSegment") }
        
        val rootPath = File(filesDir, ATTACHMENTS_ROOT).canonicalFile.toPath()
        val file = File(filesDir, storedLocation)
        val candidatePath = file.canonicalFile.toPath()

        if (!candidatePath.startsWith(rootPath)) {
            throw IllegalArgumentException("Path traversal attempt outside attachments: $storedLocation")
        }
        return file
    }

    /**
     * Validates that a string is a safe filesystem path segment.
     */
    fun validateSegment(value: String, fieldName: String) {
        if (value.isBlank()) throw IllegalArgumentException("Blank path segment: $fieldName")
        if (value == "." || value == "..") throw IllegalArgumentException("Traversal segment rejected: $fieldName")
        if (value.contains("/") || value.contains("\\") || value.contains("\u0000")) {
            throw IllegalArgumentException("Illegal characters in path segment ($fieldName): $value")
        }
        if (value.any { it.isISOControl() }) {
            throw IllegalArgumentException("Control characters in path segment: $fieldName")
        }
        if (value.trim() != value) {
            throw IllegalArgumentException("Leading/trailing whitespace in path segment: $fieldName")
        }
    }

    /**
     * Internal resolver restricted to the purchase document root.
     */
    private fun resolveUnderPurchaseRoot(filesDir: File, storedLocation: String): File {
        val purchaseRoot = File(filesDir, ROOT_DIR).canonicalFile
        val purchaseRootPath = purchaseRoot.toPath()
        
        val file = File(filesDir, storedLocation)
        val candidatePath = file.canonicalFile.toPath()
        
        if (!candidatePath.startsWith(purchaseRootPath)) {
            throw IllegalArgumentException("Path traversal attempt outside purchase root: $storedLocation")
        }
        
        return file
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
