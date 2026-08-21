package com.venkoi.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoice_draft_applications",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReceiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchaseInvoiceParseResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["parseResultId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["purchaseReceiptId"], unique = true),
        Index("parseResultId")
    ]
)
data class PurchaseInvoiceDraftApplicationEntity(
    @PrimaryKey val id: String,
    val purchaseReceiptId: String,
    val parseResultId: String,
    val sourceDocumentSha256: String,
    val sourceStateFingerprint: String,
    val appliedAt: Long,
    val duplicateOverrideType: String? = null,
    val duplicateExistingReceiptId: String? = null,
    val duplicateNormalizedInvoiceNumber: String? = null,
    val duplicateSourceSha256: String? = null,
    val duplicateOverriddenAt: Long? = null
)
