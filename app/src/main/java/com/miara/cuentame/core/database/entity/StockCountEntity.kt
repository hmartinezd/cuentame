package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_counts",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("status"),
        Index("effectiveAt"),
        Index("restaurantId", "effectiveAt")
    ]
)
data class StockCountEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val startedAt: Long,
    val effectiveAt: Long,
    val completedAt: Long?,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val voidedAt: Long?
)
