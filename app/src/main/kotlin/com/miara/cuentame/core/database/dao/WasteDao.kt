package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.miara.cuentame.core.database.entity.WasteEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: WasteEventEntity)

    @Update
    suspend fun update(event: WasteEventEntity): Int

    @Query("DELETE FROM waste_events WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraft(id: String): Int

    @Query("SELECT * FROM waste_events WHERE id = :id")
    suspend fun getById(id: String): WasteEventEntity?

    @Query("SELECT * FROM waste_events WHERE id = :id")
    fun observeById(id: String): Flow<WasteEventEntity?>

    @Query("""
        SELECT * FROM waste_events 
        WHERE restaurantId = :restaurantId 
        AND (:status IS NULL OR status = :status)
        ORDER BY effectiveAt DESC, updatedAt DESC, createdAt DESC, id ASC
    """)
    fun observeFiltered(
        restaurantId: String,
        status: String?
    ): Flow<List<WasteEventEntity>>
}
