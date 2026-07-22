package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.StockCountDraftRepository
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.count.StockCountArea
import com.miara.cuentame.core.model.count.StockCountLine
import com.miara.cuentame.core.model.inventory.StockCountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomStockCountDraftRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val stockCountDao: StockCountDao
) : StockCountDraftRepository {

    override fun observeDraftCounts(): Flow<List<StockCount>> {
        return stockCountDao.observeCountsByStatus(StockCountStatus.DRAFT.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDraftCount(id: StockCountId): StockCount? {
        return stockCountDao.getCountById(id.value)?.toDomain()
    }

    override suspend fun getDraftAreas(id: StockCountId): List<StockCountArea> {
        return stockCountDao.observeAreasForCount(id.value).first().map { it.toDomain() }
    }

    override suspend fun getDraftLines(areaId: StockCountAreaId): List<StockCountLine> {
        return stockCountDao.observeLinesForArea(areaId.value).first().map { it.toDomain() }
    }

    override suspend fun saveDraft(
        count: StockCount,
        areas: List<StockCountArea>,
        lines: Map<StockCountAreaId, List<StockCountLine>>
    ) {
        if (count.status != StockCountStatus.DRAFT) {
            throw IllegalArgumentException("Only DRAFT counts can be saved via this repository")
        }
        database.withTransaction {
            stockCountDao.upsertCount(count.toEntity())
            
            // Re-sync areas and lines
            // In a more complex app we'd do smart diffing, but for foundation this ensures clean state
            stockCountDao.deleteAreasForCount(count.id.value)
            stockCountDao.upsertCountAreas(areas.map { it.toEntity() })
            
            areas.forEach { area ->
                stockCountDao.deleteLinesForArea(area.id.value)
                lines[area.id]?.let { areaLines ->
                    stockCountDao.upsertCountLines(areaLines.map { it.toEntity() })
                }
            }
        }
    }

    override suspend fun deleteDraft(id: StockCountId) {
        database.withTransaction {
            val areas = stockCountDao.observeAreasForCount(id.value).first()
            areas.forEach { area ->
                stockCountDao.deleteLinesForArea(area.id)
            }
            stockCountDao.deleteAreasForCount(id.value)
            stockCountDao.deleteDraftCount(id.value)
        }
    }
}
