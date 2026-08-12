package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "stock_count_item_order",
    primaryKeys = ["areaId", "ingredientId"],
    foreignKeys = [
        ForeignKey(entity = RestaurantEntity::class, parentColumns = ["id"], childColumns = ["restaurantId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InventoryAreaEntity::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("restaurantId"), Index("ingredientId"), Index(value = ["areaId", "sortOrder"])]
)
data class StockCountItemOrderEntity(
    val restaurantId: String,
    val areaId: String,
    val ingredientId: String,
    val sortOrder: Int,
    val updatedAt: Long
)
