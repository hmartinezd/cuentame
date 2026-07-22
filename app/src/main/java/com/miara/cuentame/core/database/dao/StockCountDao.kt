package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.miara.cuentame.core.database.entity.StockCountAreaEntity
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.database.entity.StockCountLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockCountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCount(count: StockCountEntity)

    @Update
    suspend fun updateCount(count: StockCountEntity)

    @Query("DELETE FROM stock_counts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftCount(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountAreas(areas: List<StockCountAreaEntity>)

    @Query("DELETE FROM stock_count_areas WHERE stockCountId = :countId")
    suspend fun deleteAreasForCount(countId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountLines(lines: List<StockCountLineEntity>)

    @Query("DELETE FROM stock_count_lines WHERE stockCountAreaId = :areaId")
    suspend fun deleteLinesForArea(areaId: String)

    @Query("SELECT * FROM stock_counts WHERE status = :status ORDER BY effectiveAt DESC")
    fun observeCountsByStatus(status: String): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_counts WHERE id = :id")
    suspend fun getCountById(id: String): StockCountEntity?

    @Query("SELECT * FROM stock_count_areas WHERE stockCountId = :countId ORDER BY sortOrder")
    fun observeAreasForCount(countId: String): Flow<List<StockCountAreaEntity>>

    @Query("SELECT * FROM stock_count_lines WHERE stockCountAreaId = :areaId")
    fun observeLinesForArea(areaId: String): Flow<List<StockCountLineEntity>>
}
