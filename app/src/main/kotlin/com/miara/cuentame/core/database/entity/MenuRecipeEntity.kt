package com.miara.cuentame.core.database.entity

import androidx.room.*
import java.math.BigDecimal

@Entity(
    tableName = "menu_recipes",
    foreignKeys = [ForeignKey(entity = RestaurantEntity::class, parentColumns = ["id"], childColumns = ["restaurantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("restaurantId"), Index(value = ["restaurantId", "normalizedName"]), Index("archivedAt")]
)
data class MenuRecipeEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sellingPrice: BigDecimal?,
    val notes: String?,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
