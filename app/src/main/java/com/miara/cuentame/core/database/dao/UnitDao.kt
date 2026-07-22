package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miara.cuentame.core.database.entity.UnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY sortOrder")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE dimension = :dimension ORDER BY sortOrder")
    fun observeByDimension(dimension: String): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: String): UnitEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedUnits(units: List<UnitEntity>)

    @Query("SELECT COUNT(*) FROM units WHERE isSystem = 1")
    suspend fun countSeededUnits(): Int
}
