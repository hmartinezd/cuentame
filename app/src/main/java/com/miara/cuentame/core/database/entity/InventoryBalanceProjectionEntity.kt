package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "inventory_balance_projection",
    primaryKeys = ["restaurantId", "ingredientId", "areaId"],
    indices = [
        Index("restaurantId"),
        Index("ingredientId"),
        Index("areaId")
    ]
)
data class InventoryBalanceProjectionEntity(
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val quantityBase: String,
    val updatedAt: Long
)
