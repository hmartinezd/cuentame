package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryAreaDao {
    @Query("SELECT * FROM inventory_areas WHERE isActive = 1 AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeActiveAreas(): Flow<List<InventoryAreaEntity>>

    @Query("SELECT * FROM inventory_areas WHERE deletedAt IS NULL ORDER BY sortOrder")
    fun observeAllAreas(): Flow<List<InventoryAreaEntity>>

    @Query("SELECT * FROM inventory_areas WHERE id = :id")
    suspend fun getById(id: String): InventoryAreaEntity?

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): InventoryAreaEntity?

    @Upsert
    suspend fun upsert(area: InventoryAreaEntity)

    @Query("UPDATE inventory_areas SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Update
    suspend fun updateAll(areas: List<InventoryAreaEntity>)
}
