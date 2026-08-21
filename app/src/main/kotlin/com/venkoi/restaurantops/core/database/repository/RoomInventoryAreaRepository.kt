package com.venkoi.restaurantops.core.database.repository

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.dao.InventoryAreaDao
import com.venkoi.restaurantops.core.database.dao.RestaurantDao
import com.venkoi.restaurantops.core.database.mapper.toDomain
import com.venkoi.restaurantops.core.database.mapper.toEntity
import com.venkoi.restaurantops.core.domain.repository.InventoryAreaRepository
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomInventoryAreaRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val inventoryAreaDao: InventoryAreaDao,
    private val restaurantDao: RestaurantDao,
    private val batchDao: com.venkoi.restaurantops.core.database.dao.ProductionBatchDao
) : InventoryAreaRepository {
    override fun observeActiveAreas(): Flow<List<InventoryArea>> {
        return restaurantDao.observeRestaurant().flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else inventoryAreaDao.observeActiveAreas(restaurant.id).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override fun observeAllAreas(): Flow<List<InventoryArea>> {
        return restaurantDao.observeRestaurant().flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else inventoryAreaDao.observeAllAreas(restaurant.id).map { entities ->
                entities.map { it.toDomain() }
            }
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

            // Production Batches
            if (batchDao.countDraftsUsingOutputArea(id.value) > 0) throw ValidationError.AreaUsedByProductionDraft
            if (batchDao.countDraftsUsingComponentSourceArea(id.value) > 0) throw ValidationError.AreaUsedByProductionDraft

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
