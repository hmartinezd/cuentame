package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomInventoryAreaRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val inventoryAreaDao: InventoryAreaDao
) : InventoryAreaRepository {
    override fun observeActiveAreas(): Flow<List<InventoryArea>> {
        return inventoryAreaDao.observeActiveAreas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllAreas(): Flow<List<InventoryArea>> {
        return inventoryAreaDao.observeAllAreas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: InventoryAreaId): InventoryArea? {
        return inventoryAreaDao.getById(id.value)?.toDomain()
    }

    override suspend fun save(area: InventoryArea) {
        val normalizedName = area.name.normalizeName()
        if (normalizedName.isBlank()) throw ValidationError.InvalidName
        
        val duplicate = inventoryAreaDao.findByNormalizedName(area.restaurantId.value, normalizedName)
        if (duplicate != null && duplicate.id != area.id.value) throw ValidationError.DuplicateActiveName

        inventoryAreaDao.upsert(area.copy(normalizedName = normalizedName).toEntity())
    }

    override suspend fun archive(id: InventoryAreaId, at: Instant) {
        database.withTransaction {
            val entity = inventoryAreaDao.getById(id.value) ?: throw ValidationError.RecordNotFound
            val activeCount = inventoryAreaDao.getActiveCount(entity.restaurantId)
            if (activeCount <= 1) {
                throw ValidationError.FinalAreaCannotBeArchived
            }
            inventoryAreaDao.softArchive(id.value, at.toEpochMilli())
        }
    }

    override suspend fun reorder(ids: List<InventoryAreaId>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            val firstEntity = inventoryAreaDao.getById(ids.first().value) ?: throw ValidationError.InvalidSetupState
            val restaurantId = firstEntity.restaurantId
            
            val activeIds = inventoryAreaDao.getActiveIds(restaurantId).toSet()
            val inputIds = ids.map { it.value }.toSet()
            
            if (inputIds.size != ids.size) throw ValidationError.InvalidSetupState
            if (inputIds != activeIds) throw ValidationError.InvalidSetupState
            
            ids.forEachIndexed { index, id ->
                val entity = inventoryAreaDao.getById(id.value)!!
                inventoryAreaDao.upsert(entity.copy(sortOrder = index))
            }
        }
    }
}
