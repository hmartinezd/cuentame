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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCount(count: StockCountEntity)

    @Update
    suspend fun updateCount(count: StockCountEntity): Int

    @Query("DELETE FROM stock_counts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCountAreas(areas: List<StockCountAreaEntity>)

    @Update
    suspend fun updateCountArea(area: StockCountAreaEntity): Int

    @Query("DELETE FROM stock_count_areas WHERE stockCountId = :countId")
    suspend fun deleteAreasForCount(countId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCountLine(line: StockCountLineEntity)

    @Update
    suspend fun updateCountLine(line: StockCountLineEntity): Int

    @Query("DELETE FROM stock_count_lines WHERE id = :id")
    suspend fun deleteLine(id: String): Int

    @Query("DELETE FROM stock_count_lines WHERE stockCountAreaId = :areaId")
    suspend fun deleteLinesForArea(areaId: String)

    @Query("""
        SELECT * FROM stock_counts 
        WHERE restaurantId = :restaurantId 
        AND (:status IS NULL OR status = :status)
        AND (:query IS NULL OR name LIKE '%' || :query || '%')
        ORDER BY effectiveAt DESC, updatedAt DESC, createdAt DESC, id ASC
    """)
    fun observeFilteredCounts(
        restaurantId: String,
        status: String?,
        query: String?
    ): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_counts WHERE id = :id")
    suspend fun getCountById(id: String): StockCountEntity?

    @Query("SELECT * FROM stock_counts WHERE id = :id")
    fun observeCountById(id: String): Flow<StockCountEntity?>

    @Query("SELECT * FROM stock_count_areas WHERE stockCountId = :countId ORDER BY sortOrder ASC")
    suspend fun getAreasForCount(countId: String): List<StockCountAreaEntity>

    @Query("SELECT * FROM stock_count_areas WHERE stockCountId = :countId ORDER BY sortOrder ASC")
    fun observeAreasForCount(countId: String): Flow<List<StockCountAreaEntity>>

    @Query("SELECT * FROM stock_count_areas WHERE id = :id")
    suspend fun getAreaById(id: String): StockCountAreaEntity?

    @Query("SELECT * FROM stock_count_areas WHERE id = :id")
    fun observeAreaById(id: String): Flow<StockCountAreaEntity?>

    @Query("SELECT * FROM stock_count_lines WHERE stockCountAreaId = :areaId ORDER BY createdAt ASC")
    suspend fun getLinesForArea(areaId: String): List<StockCountLineEntity>

    @Query("SELECT * FROM stock_count_lines WHERE stockCountAreaId = :areaId ORDER BY createdAt ASC")
    fun observeLinesForArea(areaId: String): Flow<List<StockCountLineEntity>>

    @Query("SELECT * FROM stock_count_lines WHERE id = :id")
    suspend fun getLineById(id: String): StockCountLineEntity?

    @Query("""
        SELECT scl.* FROM stock_count_lines scl
        JOIN stock_count_areas sca ON scl.stockCountAreaId = sca.id
        WHERE sca.stockCountId = :countId
    """)
    suspend fun getAllLinesForCount(countId: String): List<StockCountLineEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM stock_counts sc
            JOIN stock_count_areas sca ON sc.id = sca.stockCountId
            WHERE sc.status = 'DRAFT' AND sca.areaId = :areaId
        )
    """)
    suspend fun isAreaInAnyDraftCount(areaId: String): Boolean

    @Transaction
    suspend fun deleteDraftWithGraph(countId: String) {
        val areas = getAreasForCount(countId)
        areas.forEach { deleteLinesForArea(it.id) }
        deleteAreasForCount(countId)
        deleteDraftCount(countId)
    }
}
