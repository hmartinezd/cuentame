package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryProjectionDao {
    @Query("SELECT * FROM inventory_balance_projection")
    fun observeAllBalances(): Flow<List<InventoryBalanceProjectionEntity>>

    @Query("SELECT * FROM inventory_balance_projection WHERE ingredientId = :ingredientId")
    fun observeBalancesForIngredient(ingredientId: String): Flow<List<InventoryBalanceProjectionEntity>>

    @Query("SELECT * FROM inventory_balance_projection WHERE ingredientId = :ingredientId AND areaId = :areaId")
    suspend fun getBalance(ingredientId: String, areaId: String): InventoryBalanceProjectionEntity?

    @Upsert
    suspend fun upsert(projection: InventoryBalanceProjectionEntity)

    @Query("DELETE FROM inventory_balance_projection WHERE ingredientId = :ingredientId")
    suspend fun deleteForIngredient(ingredientId: String)
}
