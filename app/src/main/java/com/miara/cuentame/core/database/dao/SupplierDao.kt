package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.miara.cuentame.core.database.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE restaurantId = :restaurantId AND isActive = 1 AND deletedAt IS NULL ORDER BY name ASC")
    fun observeActiveSuppliers(restaurantId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE restaurantId = :restaurantId ORDER BY isActive DESC, name ASC")
    fun observeAllSuppliers(restaurantId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun observeById(id: String): Flow<SupplierEntity?>

    @Query("SELECT * FROM suppliers WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(supplier: SupplierEntity)

    @Update
    suspend fun update(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET isActive = 0, deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)
}
