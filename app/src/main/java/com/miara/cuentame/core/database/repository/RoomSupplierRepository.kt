package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.supplier.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomSupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao
) : SupplierRepository {
    override fun observeActiveSuppliers(): Flow<List<Supplier>> {
        return supplierDao.observeActiveSuppliers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: SupplierId): Supplier? {
        return supplierDao.getById(id.value)?.toDomain()
    }

    override suspend fun save(supplier: Supplier) {
        val normalizedName = supplier.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName

        val duplicate = supplierDao.findByNormalizedName(supplier.restaurantId.value, normalizedName)
        if (duplicate != null && duplicate.id != supplier.id.value) throw ValidationError.DuplicateActiveName

        supplierDao.upsert(supplier.copy(normalizedName = normalizedName).toEntity())
    }

    override suspend fun archive(id: SupplierId, at: Instant) {
        supplierDao.softArchive(id.value, at.toEpochMilli())
    }
}
