package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: InventoryMovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movements: List<InventoryMovementEntity>)

    @Query("DELETE FROM inventory_movements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    fun observeByIngredient(ingredientId: String): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId AND areaId = :areaId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    fun observeByIngredientAndArea(ingredientId: String, areaId: String): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    suspend fun getByIngredient(ingredientId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE areaId = :areaId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    suspend fun getByArea(areaId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE reversalOfMovementId = :originalMovementId LIMIT 1")
    suspend fun findReversalFor(originalMovementId: String): InventoryMovementEntity?

    @Query("SELECT * FROM inventory_movements WHERE sourceDocumentType = :type AND sourceDocumentId = :docId")
    suspend fun getBySourceDocument(type: String, docId: String): List<InventoryMovementEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE sourceDocumentType = :type AND sourceDocumentId = :docId AND sourceOperationId = :opId LIMIT 1)")
    suspend fun existsBySourceOperation(type: String, docId: String, opId: String): Boolean
}
