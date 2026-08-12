package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.model.InventoryValuationRow
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryProjectionDao {
    @Query("""
        SELECT 
            ibp.ingredientId,
            i.name as ingredientName,
            u.symbol as baseUnitSymbol,
            ibp.quantityBase, 
            icp.averageUnitCostBase,
            ibp.areaId
        FROM inventory_balance_projection ibp
        JOIN ingredients i ON ibp.ingredientId = i.id AND i.restaurantId = ibp.restaurantId
        JOIN units u ON i.baseUnitId = u.id
        LEFT JOIN ingredient_cost_projection icp 
            ON ibp.restaurantId = icp.restaurantId 
            AND ibp.ingredientId = icp.ingredientId
        WHERE ibp.restaurantId = :restaurantId
    """)
    fun observeValuationRows(restaurantId: String): Flow<List<InventoryValuationRow>>

    @Query("SELECT * FROM inventory_balance_projection")
    fun observeAllBalances(): Flow<List<InventoryBalanceProjectionEntity>>

    @Query("SELECT * FROM inventory_balance_projection WHERE restaurantId = :restaurantId")
    fun observeBalancesForRestaurant(restaurantId: String): Flow<List<InventoryBalanceProjectionEntity>>

    @Query("SELECT * FROM inventory_balance_projection WHERE ingredientId = :ingredientId")
    fun observeBalancesForIngredient(ingredientId: String): Flow<List<InventoryBalanceProjectionEntity>>

    @Query("SELECT * FROM inventory_balance_projection WHERE ingredientId = :ingredientId AND areaId = :areaId")
    suspend fun getBalance(ingredientId: String, areaId: String): InventoryBalanceProjectionEntity?

    @Query("SELECT TOTAL(quantityBase) FROM inventory_balance_projection WHERE ingredientId = :ingredientId")
    suspend fun getTotalBalance(ingredientId: String): Double // Room's TOTAL() returns Double, or I can use SUM and handle null

    @Upsert
    suspend fun upsert(projection: InventoryBalanceProjectionEntity)

    @Query("DELETE FROM inventory_balance_projection WHERE ingredientId = :ingredientId")
    suspend fun deleteForIngredient(ingredientId: String)

    @Query("SELECT * FROM inventory_balance_projection")
    suspend fun getAll(): List<InventoryBalanceProjectionEntity>
}
