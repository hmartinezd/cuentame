package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "production_batches",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PreparationRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["outputIngredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["outputAreaId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["outputUnitOptionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("recipeId"),
        Index("outputIngredientId"),
        Index("outputAreaId"),
        Index("outputUnitOptionId"),
        Index("status"),
        Index("effectiveAt"),
        Index("restaurantId", "effectiveAt"),
        Index("restaurantId", "status")
    ]
)
data class ProductionBatchEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val recipeId: String,

    val recipeNameSnapshot: String,
    val outputIngredientId: String,

    val batchMultiplier: String,

    val recipeStandardYieldQuantitySnapshot: String,
    val recipeStandardYieldBaseSnapshot: String,
    val recipeYieldUnitOptionIdSnapshot: String,

    val expectedOutputQuantityEntered: String,
    val expectedOutputQuantityBase: String,

    val actualOutputQuantityEntered: String,
    val actualOutputQuantityBase: String,
    val outputUnitOptionId: String,
    val outputAreaId: String,
    val hasManualOutputQuantityOverride: Boolean,

    val totalComponentCostSnapshot: String?,
    val outputUnitCostBaseSnapshot: String?,

    val effectiveAt: Long,
    val status: String,
    val notes: String?,

    val createdAt: Long,
    val updatedAt: Long,
    val postedAt: Long?,
    val voidedAt: Long?
)
