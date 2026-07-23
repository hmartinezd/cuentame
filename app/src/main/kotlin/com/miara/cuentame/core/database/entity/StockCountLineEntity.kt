package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_count_lines",
    foreignKeys = [
        ForeignKey(
            entity = StockCountAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockCountAreaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientUnitOptionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("stockCountAreaId"),
        Index("ingredientId"),
        Index("ingredientUnitOptionId"),
        Index("stockCountAreaId", "ingredientId", unique = true)
    ]
)
data class StockCountLineEntity(
    @PrimaryKey val id: String,
    val stockCountAreaId: String,
    val ingredientId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val expectedQuantityBaseSnapshot: String?,
    val adjustmentQuantityBase: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
