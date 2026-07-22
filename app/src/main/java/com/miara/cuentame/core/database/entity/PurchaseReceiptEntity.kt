package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_receipts",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("supplierId"),
        Index("purchaseDate"),
        Index("status"),
        Index("restaurantId", "purchaseDate")
    ]
)
data class PurchaseReceiptEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val supplierId: String?,
    val invoiceNumber: String?,
    val purchaseDate: Long,
    val status: String,
    val notes: String?,
    val attachmentPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)
