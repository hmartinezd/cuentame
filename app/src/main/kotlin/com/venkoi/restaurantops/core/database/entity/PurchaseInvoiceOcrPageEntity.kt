package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "purchase_invoice_ocr_pages",
    primaryKeys = [
        "ocrResultId",
        "pageIndex"
    ],
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceOcrResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["ocrResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("ocrResultId")
    ]
)
data class PurchaseInvoiceOcrPageEntity(
    val ocrResultId: String,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val text: String,
    val evidenceJson: String
)
