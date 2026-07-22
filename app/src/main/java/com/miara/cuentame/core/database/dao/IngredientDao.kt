package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL ORDER BY normalizedName ASC, id ASC")
    fun observeActiveIngredients(restaurantId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE restaurantId = :restaurantId ORDER BY normalizedName ASC, id ASC")
    fun observeAllIngredients(restaurantId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeIngredient(id: String): Flow<IngredientEntity?>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getById(id: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): IngredientEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: IngredientEntity)

    @Update
    suspend fun update(ingredient: IngredientEntity)

    @Upsert
    suspend fun upsert(ingredient: IngredientEntity)

    @Query("UPDATE ingredients SET isActive = 0, deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE ingredientId = :ingredientId LIMIT 1)")
    suspend fun hasMovements(ingredientId: String): Boolean
}
