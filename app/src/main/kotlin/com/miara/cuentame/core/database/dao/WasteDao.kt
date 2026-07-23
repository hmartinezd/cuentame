package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.WasteEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Upsert
    suspend fun upsert(event: WasteEventEntity)

    @Query("DELETE FROM waste_events WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftEvent(id: String)

    @Query("SELECT * FROM waste_events ORDER BY effectiveAt DESC")
    fun observeEvents(): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events WHERE id = :id")
    suspend fun getById(id: String): WasteEventEntity?
}
