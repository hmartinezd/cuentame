package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.model.supplier.Supplier
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SupplierRepository {
    fun observeActiveSuppliers(): Flow<List<Supplier>>
    suspend fun getById(id: SupplierId): Supplier?
    suspend fun save(supplier: Supplier)
    suspend fun archive(id: SupplierId, at: Instant)
}
