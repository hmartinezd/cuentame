package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoice_line_origins",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseLineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchaseInvoiceDraftApplicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["applicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseLineId"], unique = true),
        Index("applicationId"),
        Index(value = ["applicationId", "sourceLineIndex"], unique = true)
    ]
)
data class PurchaseInvoiceLineOriginEntity(
    @PrimaryKey val purchaseLineId: String,
    val applicationId: String,
    val sourceLineIndex: Int,
    val sourceStateFingerprint: String,
    val lastMaterializedSnapshotJson: String
)
