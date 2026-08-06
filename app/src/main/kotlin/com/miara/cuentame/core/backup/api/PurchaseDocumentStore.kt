package com.miara.cuentame.core.backup.api

import android.net.Uri
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
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
        sourceUri: Uri
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
    
    companion object {
        val SUPPORTED_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
