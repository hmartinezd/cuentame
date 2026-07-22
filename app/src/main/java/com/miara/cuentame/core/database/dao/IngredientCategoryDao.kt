package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientCategoryDao {
    @Query("SELECT * FROM ingredient_categories WHERE isActive = 1 AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeActiveCategories(): Flow<List<IngredientCategoryEntity>>

    @Query("SELECT * FROM ingredient_categories WHERE deletedAt IS NULL ORDER BY sortOrder")
    fun observeAllCategories(): Flow<List<IngredientCategoryEntity>>

    @Query("SELECT * FROM ingredient_categories WHERE id = :id")
    suspend fun getById(id: String): IngredientCategoryEntity?

    @Query("SELECT * FROM ingredient_categories WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): IngredientCategoryEntity?

    @Upsert
    suspend fun upsert(category: IngredientCategoryEntity)

    @Query("UPDATE ingredient_categories SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Update
    suspend fun updateAll(categories: List<IngredientCategoryEntity>)
}
