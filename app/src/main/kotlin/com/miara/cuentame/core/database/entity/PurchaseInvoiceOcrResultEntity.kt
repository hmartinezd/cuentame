package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoice_ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReceiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseReceiptId"], unique = true),
        Index("sourceDocumentSha256"),
        Index("processedAt")
    ]
)
data class PurchaseInvoiceOcrResultEntity(
    @PrimaryKey val id: String,
    val purchaseReceiptId: String,
    val sourceDocumentSha256: String,
    val sourceMimeType: String,
    val engine: String,
    val evidenceSchemaVersion: Int,
    val pageCount: Int,
    val fullText: String,
    val processedAt: Long
)
