package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "units",
    indices = [
        Index("dimension"),
        Index("sortOrder")
    ]
)
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val dimension: String,
    val factorToCanonical: BigDecimal,
    val isSystem: Boolean,
    val sortOrder: Int
)
