package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.CreateSupplierCommand
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UpdateSupplierCommand
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.supplier.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomSupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao,
    private val restaurantDao: RestaurantDao,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : SupplierRepository {

    private suspend fun validateRestaurant(restaurantId: RestaurantId) {
        val active = restaurantDao.getRestaurant() ?: throw ValidationError.RecordNotFound
        if (active.id != restaurantId.value) throw ValidationError.SupplierOwnershipMismatch
        if (active.deletedAt != null) throw ValidationError.ArchivedReference
    }

    override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> {
        val flow = if (includeArchived) {
            supplierDao.observeAllSuppliers(restaurantId.value)
        } else {
            supplierDao.observeActiveSuppliers(restaurantId.value)
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeSupplier(id: SupplierId): Flow<Supplier?> {
        return supplierDao.observeById(id.value).map { it?.toDomain() }
    }

    override suspend fun getSupplier(id: SupplierId): Supplier? {
        return supplierDao.getById(id.value)?.toDomain()
    }

    override suspend fun createSupplier(command: CreateSupplierCommand): SupplierId {
        validateRestaurant(command.restaurantId)

        val normalizedName = command.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName
        
        val existing = supplierDao.findByNormalizedName(command.restaurantId.value, normalizedName)
        if (existing != null) throw ValidationError.SupplierNameAlreadyExists

        validateEmail(command.email)

        val now = timeProvider.now()
        val supplier = Supplier(
            id = SupplierId(idGenerator.newId()),
            restaurantId = command.restaurantId,
            name = command.name.trim(),
            normalizedName = normalizedName,
            phone = command.phone?.trim()?.ifBlank { null },
            email = command.email?.trim()?.ifBlank { null },
            notes = command.notes?.trim()?.ifBlank { null },
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        supplierDao.insert(supplier.toEntity())
        return supplier.id
    }

    override suspend fun updateSupplier(command: UpdateSupplierCommand) {
        val existingEntity = supplierDao.getById(command.supplierId.value)
            ?: throw ValidationError.SupplierNotFound
        
        validateRestaurant(RestaurantId(existingEntity.restaurantId))

        if (!existingEntity.isActive || existingEntity.deletedAt != null) {
            throw ValidationError.SupplierArchived
        }

        val normalizedName = command.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName
        
        val duplicate = supplierDao.findByNormalizedName(existingEntity.restaurantId, normalizedName)
        if (duplicate != null && duplicate.id != existingEntity.id) {
            throw ValidationError.SupplierNameAlreadyExists
        }

        validateEmail(command.email)

        val updated = existingEntity.copy(
            name = command.name.trim(),
            normalizedName = normalizedName,
            phone = command.phone?.trim()?.ifBlank { null },
            email = command.email?.trim()?.ifBlank { null },
            notes = command.notes?.trim()?.ifBlank { null },
            updatedAt = timeProvider.now().toEpochMilli()
        )

        supplierDao.update(updated)
    }

    override suspend fun archiveSupplier(id: SupplierId, at: Instant) {
        val existing = supplierDao.getById(id.value) ?: throw ValidationError.SupplierNotFound
        if (existing.deletedAt != null) return
        supplierDao.softArchive(id.value, at.toEpochMilli())
    }

    private fun validateEmail(email: String?) {
        if (email.isNullOrBlank()) return
        val regex = "^[^@]+@[^@]+\\.[^@]+$".toRegex()
        if (!regex.matches(email.trim())) {
            throw ValidationError.InvalidSupplierEmail
        }
    }
}
