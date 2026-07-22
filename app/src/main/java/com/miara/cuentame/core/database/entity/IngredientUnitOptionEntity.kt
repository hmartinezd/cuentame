package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "ingredient_unit_options",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["standardUnitId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("ingredientId"),
        Index("standardUnitId"),
        Index("ingredientId", "isBase"),
        Index("ingredientId", "isActive")
    ]
)
data class IngredientUnitOptionEntity(
    @PrimaryKey val id: String,
    val ingredientId: String,
    val displayName: String,
    val shortLabel: String,
    val standardUnitId: String?,
    val factorToBase: BigDecimal,
    val isBase: Boolean,
    val isDefaultCount: Boolean,
    val isDefaultPurchase: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)
