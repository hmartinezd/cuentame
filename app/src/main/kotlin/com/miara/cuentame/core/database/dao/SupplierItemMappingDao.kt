package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.SupplierItemMappingEntity
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierItemMappingDao {

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId AND supplierId = :supplierId AND keyType = :keyType AND normalizedKey = :normalizedKey")
    suspend fun getMapping(
        restaurantId: String,
        supplierId: String,
        keyType: SupplierItemMappingKeyType,
        normalizedKey: String
    ): SupplierItemMappingEntity?

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId AND supplierId = :supplierId")
    fun observeMappingsForSupplier(restaurantId: String, supplierId: String): Flow<List<SupplierItemMappingEntity>>

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId AND supplierId = :supplierId")
    suspend fun getMappingsForSupplierSync(restaurantId: String, supplierId: String): List<SupplierItemMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: SupplierItemMappingEntity)

    @Delete
    suspend fun deleteMapping(mapping: SupplierItemMappingEntity)
    
    @Query("DELETE FROM supplier_item_mappings WHERE id = :id")
    suspend fun deleteMappingById(id: String)
}
