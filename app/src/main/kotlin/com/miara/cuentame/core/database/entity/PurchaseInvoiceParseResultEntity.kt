package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoice_parse_results",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReceiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchaseInvoiceOcrResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["ocrResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseReceiptId"], unique = true),
        Index(value = ["ocrResultId"], unique = true),
        Index("sourceDocumentSha256"),
        Index("processedAt")
    ]
)
data class PurchaseInvoiceParseResultEntity(
    @PrimaryKey val id: String,
    val purchaseReceiptId: String,
    val ocrResultId: String,
    val sourceDocumentSha256: String,
    val parserEngine: String,
    val parserSchemaVersion: Int,
    val headerEvidenceJson: String,
    val totalsEvidenceJson: String,
    val correctionsJson: String?,
    val warningsJson: String,
    val processedAt: Long,
    val reviewedAt: Long?
)
