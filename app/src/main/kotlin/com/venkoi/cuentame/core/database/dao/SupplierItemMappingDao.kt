package com.venkoi.cuentame.core.database.dao

import androidx.room.*
import com.venkoi.cuentame.core.database.entity.SupplierItemMappingEntity
import com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierItemMappingDao {

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId ORDER BY supplierId, ingredientId, id")
    fun observeAllMappings(restaurantId: String): Flow<List<SupplierItemMappingEntity>>

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

    @Query("SELECT * FROM supplier_item_mappings WHERE id = :id")
    suspend fun getMappingById(id: String): SupplierItemMappingEntity?

    @Query("SELECT * FROM supplier_item_mappings WHERE restaurantId = :restaurantId")
    suspend fun getAllMappingsSync(restaurantId: String): List<SupplierItemMappingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMappingStrict(mapping: SupplierItemMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: SupplierItemMappingEntity)

    @Delete
    suspend fun deleteMapping(mapping: SupplierItemMappingEntity)
    
    @Query("DELETE FROM supplier_item_mappings WHERE id = :id")
    suspend fun deleteMappingById(id: String)
}
