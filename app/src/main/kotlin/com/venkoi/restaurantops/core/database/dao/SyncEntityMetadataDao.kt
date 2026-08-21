package com.venkoi.restaurantops.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Upsert
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity

@Dao
interface SyncEntityMetadataDao {
    @Query("SELECT * FROM sync_entity_metadata ORDER BY entityType, entityId")
    suspend fun getAll(): List<SyncEntityMetadataEntity>

    @Insert
    suspend fun insertAll(metadata: List<SyncEntityMetadataEntity>)

    @Query("SELECT * FROM sync_entity_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun get(entityType: String, entityId: String): SyncEntityMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: SyncEntityMetadataEntity)

    @Query("DELETE FROM sync_entity_metadata WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: String, entityId: String)
}
