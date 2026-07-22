package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ingredient_cost_projection",
    primaryKeys = ["restaurantId", "ingredientId"],
    indices = [
        Index("restaurantId"),
        Index("ingredientId")
    ]
)
data class IngredientCostProjectionEntity(
    val restaurantId: String,
    val ingredientId: String,
    val averageUnitCostBase: String,
    val updatedAt: Long
)
