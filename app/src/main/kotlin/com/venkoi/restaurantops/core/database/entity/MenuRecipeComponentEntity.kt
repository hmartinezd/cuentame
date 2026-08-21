package com.venkoi.restaurantops.core.database.entity

import androidx.room.*
import java.math.BigDecimal

@Entity(
    tableName = "menu_recipe_components",
    foreignKeys = [
        ForeignKey(entity = MenuRecipeEntity::class, parentColumns = ["id"], childColumns = ["menuRecipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IngredientUnitOptionEntity::class, parentColumns = ["id"], childColumns = ["ingredientUnitOptionId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("menuRecipeId"), Index("ingredientId"), Index("ingredientUnitOptionId"), Index(value = ["menuRecipeId", "ingredientId"], unique = true)]
)
data class MenuRecipeComponentEntity(
    @PrimaryKey val id: String,
    val menuRecipeId: String,
    val ingredientId: String,
    val ingredientUnitOptionId: String,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
