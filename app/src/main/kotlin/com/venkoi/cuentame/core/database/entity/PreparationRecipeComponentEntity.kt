package com.venkoi.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "preparation_recipe_components",
    foreignKeys = [
        ForeignKey(
            entity = PreparationRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["componentIngredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitOptionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("recipeId"),
        Index("componentIngredientId"),
        Index("unitOptionId"),
        Index(
            value = ["recipeId", "componentIngredientId"],
            unique = true
        )
    ]
)
data class PreparationRecipeComponentEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val componentIngredientId: String,
    val unitOptionId: String,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val sortOrder: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
