package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "purchase_invoice_parsed_lines",
    primaryKeys = ["parseResultId", "lineIndex"],
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceParseResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["parseResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("parseResultId")
    ]
)
data class PurchaseInvoiceParsedLineEntity(
    val parseResultId: String,
    val lineIndex: Int,
    val evidenceJson: String,
    val correctionJson: String?,
    val isIgnored: Boolean
)
