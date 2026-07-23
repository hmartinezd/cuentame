package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.model.supplier.Supplier
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
}
