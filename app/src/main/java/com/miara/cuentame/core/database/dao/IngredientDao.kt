package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE isActive = 1 AND deletedAt IS NULL ORDER BY normalizedName ASC")
    fun observeActiveIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE deletedAt IS NULL ORDER BY normalizedName ASC")
    fun observeAllIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeIngredient(id: String): Flow<IngredientEntity?>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getById(id: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): IngredientEntity?

    @Upsert
    suspend fun upsert(ingredient: IngredientEntity)

    @Query("UPDATE ingredients SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE ingredientId = :ingredientId LIMIT 1)")
    suspend fun hasMovements(ingredientId: String): Boolean
}
