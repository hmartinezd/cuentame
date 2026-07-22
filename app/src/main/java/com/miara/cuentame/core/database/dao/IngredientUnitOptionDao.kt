package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientUnitOptionDao {
    @Query("SELECT * FROM ingredient_unit_options WHERE ingredientId = :ingredientId AND deletedAt IS NULL")
    fun observeOptionsForIngredient(ingredientId: String): Flow<List<IngredientUnitOptionEntity>>

    @Query("SELECT * FROM ingredient_unit_options WHERE ingredientId = :ingredientId AND isBase = 1 AND deletedAt IS NULL LIMIT 1")
    suspend fun getBaseOption(ingredientId: String): IngredientUnitOptionEntity?

    @Upsert
    suspend fun upsert(option: IngredientUnitOptionEntity)

    @Query("UPDATE ingredient_unit_options SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM ingredient_unit_options WHERE ingredientId = :ingredientId AND isBase = 1 AND deletedAt IS NULL")
    suspend fun countActiveBaseOptions(ingredientId: String): Int

    @Query("UPDATE ingredient_unit_options SET isDefaultCount = 0 WHERE ingredientId = :ingredientId")
    suspend fun clearDefaultCount(ingredientId: String)

    @Query("UPDATE ingredient_unit_options SET isDefaultPurchase = 0 WHERE ingredientId = :ingredientId")
    suspend fun clearDefaultPurchase(ingredientId: String)
}
