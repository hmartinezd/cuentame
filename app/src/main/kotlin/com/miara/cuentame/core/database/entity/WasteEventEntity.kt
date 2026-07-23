package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "waste_events",
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
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientUnitOptionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("ingredientId"),
        Index("areaId"),
        Index("effectiveAt"),
        Index("status"),
        Index("restaurantId", "effectiveAt")
    ]
)
data class WasteEventEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val reason: String,
    val effectiveAt: Long,
    val notes: String?,
    val attachmentPath: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)
