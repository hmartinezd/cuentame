package com.venkoi.restaurantops.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryAreaDao {
    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeActiveAreas(restaurantId: String): Flow<List<InventoryAreaEntity>>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId ORDER BY sortOrder")
    fun observeAllAreas(restaurantId: String): Flow<List<InventoryAreaEntity>>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL ORDER BY sortOrder")
    suspend fun getActiveAreasSync(restaurantId: String): List<InventoryAreaEntity>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId ORDER BY sortOrder")
    suspend fun getAllForRestaurantSync(restaurantId: String): List<InventoryAreaEntity>

    @Query("SELECT * FROM inventory_areas WHERE id = :id")
    suspend fun getById(id: String): InventoryAreaEntity?

    @Query("SELECT * FROM inventory_areas WHERE id = :id")
    fun observeById(id: String): Flow<InventoryAreaEntity?>

    @Query("SELECT * FROM inventory_areas WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): InventoryAreaEntity?

    @Query("""
        SELECT * FROM inventory_areas
        WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName
          AND deletedAt IS NULL AND id != :excludeId
        LIMIT 1
    """)
    suspend fun findOtherByNormalizedName(
        restaurantId: String,
        normalizedName: String,
        excludeId: String
    ): InventoryAreaEntity?

    @Upsert
    suspend fun upsert(area: InventoryAreaEntity)

    @Query("UPDATE inventory_areas SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM inventory_areas WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL")
    suspend fun getActiveCount(restaurantId: String): Int

    @Query("SELECT id FROM inventory_areas WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL")
    suspend fun getActiveIds(restaurantId: String): List<String>

    @Update
    suspend fun updateAll(areas: List<InventoryAreaEntity>)
}
