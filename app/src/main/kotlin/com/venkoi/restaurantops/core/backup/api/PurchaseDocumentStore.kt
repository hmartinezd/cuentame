package com.venkoi.restaurantops.core.backup.api

import android.net.Uri
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import java.io.File
import java.io.InputStream

data class StoredPurchaseDocument(
    val location: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

interface PurchaseDocumentStore {
    /**
     * Imports a document from a source URI into application-owned storage.
     * Validates type, size, and decodability before committing.
     */
    suspend fun importDocument(
        receiptId: PurchaseReceiptId,
        sourceUri: Uri,
        displayNameOverride: String? = null
    ): StoredPurchaseDocument

    /**
     * Inspects a stored document to retrieve its metadata.
     */
    suspend fun inspect(
        storedLocation: String
    ): StoredPurchaseDocument?

    /**
     * Deletes a stored document from application-owned storage.
     */
    suspend fun delete(
        storedLocation: String
    )

    /**
     * Opens a stored document for reading.
     */
    suspend fun open(
        storedLocation: String
    ): InputStream

    /**
     * Resolves a stored location to a File.
     * Use with caution, preferred for APIs requiring File or FileDescriptor (e.g. PdfRenderer).
     */
    suspend fun getFile(
        storedLocation: String
    ): File
    
    companion object {
        val SUPPORTED_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
