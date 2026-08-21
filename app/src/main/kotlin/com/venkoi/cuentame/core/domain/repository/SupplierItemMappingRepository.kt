package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.model.supplier.SupplierItemMapping
import com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType
import kotlinx.coroutines.flow.Flow

data class MappingConflict(
    val existingMapping: SupplierItemMapping,
    val newIngredientId: IngredientId,
    val newUnitOptionId: IngredientUnitOptionId?,
    val newInventoryAreaId: InventoryAreaId?
)

sealed class LearnMappingResult {
    object Learned : LearnMappingResult()
    data class Conflict(val conflict: MappingConflict) : LearnMappingResult()
    object NoChanges : LearnMappingResult()
}

interface SupplierItemMappingRepository {

    fun observeMappings(restaurantId: RestaurantId, supplierId: SupplierId): Flow<List<SupplierItemMapping>>

    suspend fun getMapping(
        restaurantId: RestaurantId,
        supplierId: SupplierId,
        keyType: SupplierItemMappingKeyType,
        normalizedKey: String
    ): SupplierItemMapping?

    suspend fun getMappingsForSupplier(
        restaurantId: RestaurantId,
        supplierId: SupplierId
    ): List<SupplierItemMapping>

    suspend fun getAllMappings(restaurantId: RestaurantId): List<SupplierItemMapping>

    /**
     * Attempts to learn a mapping from a confirmed line match.
     * @param force If true, overwrites any existing mapping without reporting a conflict.
     */
    suspend fun learnMapping(
        restaurantId: RestaurantId,
        supplierId: SupplierId,
        keyType: SupplierItemMappingKeyType,
        normalizedKey: String,
        sourceVendorCode: String?,
        sourceDescription: String?,
        sourcePackageText: String?,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?,
        force: Boolean = false
    ): LearnMappingResult

    suspend fun deleteMapping(mappingId: String)
}
