package com.venkoi.restaurantops.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.venkoi.restaurantops.core.database.entity.SyncCursorEntity

@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursors WHERE restaurantId = :restaurantId AND entityType = :entityType")
    suspend fun get(restaurantId: String, entityType: String): SyncCursorEntity?

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)
}
