package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.StockCountDraftRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.count.StockCountArea
import com.miara.cuentame.core.model.count.StockCountLine
import com.miara.cuentame.core.model.inventory.StockCountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
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
        val count = stockCountDao.getCountById(id.value)?.toDomain()
        return if (count?.status == StockCountStatus.DRAFT) count else null
    }

    override suspend fun getDraftAreas(id: StockCountId): List<StockCountArea> {
        val count = stockCountDao.getCountById(id.value)
        if (count?.status != StockCountStatus.DRAFT.name) return emptyList()
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
            throw ValidationError.ArchivedReference
        }
        
        // Validate ownership
        areas.forEach { if (it.stockCountId != count.id) throw ValidationError.MovementOwnershipMismatch }
        lines.forEach { (areaId, areaLines) ->
            if (areas.none { it.id == areaId }) throw ValidationError.MovementOwnershipMismatch
            areaLines.forEach { line ->
                if (line.stockCountAreaId != areaId) throw ValidationError.MovementOwnershipMismatch
                if (line.quantityEntered < BigDecimal.ZERO) throw ValidationError.InvalidDecimal
            }
            // Check duplicates
            val ingredientIds = areaLines.map { it.ingredientId }
            if (ingredientIds.size != ingredientIds.toSet().size) throw ValidationError.DuplicateActiveName
        }

        database.withTransaction {
            stockCountDao.upsertCount(count.toEntity())
            
            // Re-sync areas and lines
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
