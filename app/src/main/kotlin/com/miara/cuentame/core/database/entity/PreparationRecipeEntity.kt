package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "preparation_recipes",
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
            childColumns = ["outputIngredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["yieldUnitOptionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("outputIngredientId"),
        Index("yieldUnitOptionId"),
        Index(
            value = ["restaurantId", "outputIngredientId"],
            unique = true
        ),
        Index(
            value = ["restaurantId", "status"]
        )
    ]
)
data class PreparationRecipeEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val outputIngredientId: String,
    val name: String,
    val normalizedName: String,
    val standardYieldQuantity: BigDecimal?,
    val standardYieldQuantityBase: BigDecimal?,
    val yieldUnitOptionId: String?,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?
)
