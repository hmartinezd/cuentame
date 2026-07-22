package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
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
            entity = InventoryMovementEntity::class,
            parentColumns = ["id"],
            childColumns = ["reversalOfMovementId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("ingredientId"),
        Index("areaId"),
        Index("effectiveAt"),
        Index("ingredientId", "areaId", "effectiveAt"),
        Index("sourceDocumentType", "sourceDocumentId"),
        Index("sourceDocumentType", "sourceLineId", "movementType", unique = true),
        Index("reversalOfMovementId")
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val movementType: String,
    val quantityBaseSigned: String,
    val unitCostBaseSnapshot: String?,
    val totalValueSnapshot: String?,
    val effectiveAt: Long,
    val sourceDocumentType: String,
    val sourceDocumentId: String,
    val sourceLineId: String?,
    val reversalOfMovementId: String?,
    val createdAt: Long
)
