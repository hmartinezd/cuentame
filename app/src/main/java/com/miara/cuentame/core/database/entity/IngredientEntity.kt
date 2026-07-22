package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["baseUnitId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultAreaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("categoryId"),
        Index("baseUnitId"),
        Index("defaultAreaId"),
        Index("restaurantId", "normalizedName"),
        Index("restaurantId", "isActive")
    ]
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val categoryId: String?,
    val baseUnitId: String,
    val defaultAreaId: String?,
    val sku: String?,
    val notes: String?,
    val reorderPointBase: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)
