package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE isActive = 1 AND deletedAt IS NULL")
    fun observeActiveSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE restaurantId = :restaurantId AND normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun findByNormalizedName(restaurantId: String, normalizedName: String): SupplierEntity?

    @Upsert
    suspend fun upsert(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET isActive = 0, deletedAt = :at WHERE id = :id")
    suspend fun softArchive(id: String, at: Long)
}
