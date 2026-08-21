package com.venkoi.restaurantops.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY localSequence ASC")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Insert
    suspend fun insertAll(operations: List<SyncOutboxEntity>)

    @Insert
    suspend fun insert(operation: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE restaurantId = :restaurantId AND entityType = :entityType ORDER BY localSequence ASC LIMIT :limit")
    suspend fun getPending(restaurantId: String, entityType: String, limit: Int): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId ORDER BY localSequence ASC")
    suspend fun getForEntity(entityType: String, entityId: String): List<SyncOutboxEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId)")
    suspend fun hasPending(entityType: String, entityId: String): Boolean

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun deleteByOperationId(operationId: String)
}
