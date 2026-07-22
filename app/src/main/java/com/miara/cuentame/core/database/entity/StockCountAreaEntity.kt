package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_count_areas",
    foreignKeys = [
        ForeignKey(
            entity = StockCountEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockCountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("stockCountId"),
        Index("areaId"),
        Index("stockCountId", "sortOrder")
    ]
)
data class StockCountAreaEntity(
    @PrimaryKey val id: String,
    val stockCountId: String,
    val areaId: String,
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val sortOrder: Int
)
