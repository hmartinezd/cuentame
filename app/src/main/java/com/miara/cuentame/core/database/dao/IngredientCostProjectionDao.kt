package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientCostProjectionDao {
    @Query("SELECT * FROM ingredient_cost_projection WHERE ingredientId = :ingredientId")
    fun observeCostForIngredient(ingredientId: String): Flow<IngredientCostProjectionEntity?>

    @Upsert
    suspend fun upsert(projection: IngredientCostProjectionEntity)

    @Query("DELETE FROM ingredient_cost_projection WHERE ingredientId = :ingredientId")
    suspend fun deleteForIngredient(ingredientId: String)
}
