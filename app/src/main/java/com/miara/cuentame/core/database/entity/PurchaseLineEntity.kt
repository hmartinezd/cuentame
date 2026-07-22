package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReceiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["areaId"],
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
        Index("purchaseReceiptId"),
        Index("ingredientId"),
        Index("areaId"),
        Index("ingredientUnitOptionId")
    ]
)
data class PurchaseLineEntity(
    @PrimaryKey val id: String,
    val purchaseReceiptId: String,
    val ingredientId: String,
    val areaId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val lineTotal: String,
    val unitCostBase: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
