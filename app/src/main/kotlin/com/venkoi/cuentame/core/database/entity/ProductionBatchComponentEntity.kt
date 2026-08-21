package com.venkoi.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "production_batch_components",
    foreignKeys = [
        ForeignKey(
            entity = ProductionBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["productionBatchId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["componentIngredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAreaId"],
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
        Index("productionBatchId"),
        Index("componentIngredientId"),
        Index("sourceAreaId"),
        Index("unitOptionId"),
        Index("productionBatchId", "componentIngredientId", unique = true)
    ]
)
data class ProductionBatchComponentEntity(
    @PrimaryKey val id: String,
    val productionBatchId: String,

    val sourceRecipeComponentIdSnapshot: String,
    val componentIngredientId: String,

    val recipeQuantityEnteredSnapshot: String,
    val recipeQuantityBaseSnapshot: String,
    val recipeUnitOptionIdSnapshot: String,

    val expectedQuantityEntered: String,
    val expectedQuantityBase: String,

    val actualQuantityEntered: String,
    val actualQuantityBase: String,
    val unitOptionId: String,
    val hasManualQuantityOverride: Boolean,

    val sourceAreaId: String?,

    val unitCostBaseSnapshot: String?,
    val totalCostSnapshot: String?,

    val sortOrder: Int,
    val notes: String?,

    val createdAt: Long,
    val updatedAt: Long
)
