package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.model.supplier.Supplier
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CreateSupplierCommand(
    val restaurantId: RestaurantId,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null
)

data class UpdateSupplierCommand(
    val supplierId: SupplierId,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null
)

interface SupplierRepository {
    fun observeSuppliers(
        restaurantId: RestaurantId,
        includeArchived: Boolean
    ): Flow<List<Supplier>>

    fun observeSupplier(
        id: SupplierId
    ): Flow<Supplier?>

    suspend fun getSupplier(
        id: SupplierId
    ): Supplier?

    suspend fun createSupplier(
        command: CreateSupplierCommand
    ): SupplierId

    suspend fun updateSupplier(
        command: UpdateSupplierCommand
    )

    suspend fun archiveSupplier(
        id: SupplierId,
        at: Instant
    )

    suspend fun searchSuppliers(
        restaurantId: RestaurantId,
        query: String
    ): List<Supplier>
}
