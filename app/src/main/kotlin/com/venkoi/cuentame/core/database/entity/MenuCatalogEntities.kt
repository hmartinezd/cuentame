package com.venkoi.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "menus",
    foreignKeys = [ForeignKey(entity = RestaurantEntity::class, parentColumns = ["id"], childColumns = ["restaurantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("restaurantId"), Index(value = ["restaurantId", "normalizedName"]), Index("archivedAt")]
)
data class MenuEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val defaultCashDiscountPercent: BigDecimal,
    val publicationRevision: Long = 0,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "menu_categories",
    foreignKeys = [ForeignKey(entity = MenuEntity::class, parentColumns = ["id"], childColumns = ["menuId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("menuId"), Index(value = ["menuId", "normalizedName"], unique = true)]
)
data class MenuCategoryEntity(
    @PrimaryKey val id: String,
    val menuId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int
)

@Entity(
    tableName = "menu_placements",
    foreignKeys = [
        ForeignKey(entity = MenuEntity::class, parentColumns = ["id"], childColumns = ["menuId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MenuCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MenuRecipeEntity::class, parentColumns = ["id"], childColumns = ["menuRecipeId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("menuId"), Index("categoryId"), Index("menuRecipeId"), Index(value = ["menuId", "menuRecipeId"], unique = true)]
)
data class MenuPlacementEntity(
    @PrimaryKey val id: String,
    val menuId: String,
    val categoryId: String,
    val menuRecipeId: String,
    val sortOrder: Int
)
