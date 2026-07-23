package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.dao.UnitDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomUnitRepository @Inject constructor(
    private val unitDao: UnitDao
) : UnitRepository {
    override fun observeAll(): Flow<List<UnitOfMeasure>> {
        return unitDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeByDimension(dimension: UnitDimension): Flow<List<UnitOfMeasure>> {
        return unitDao.observeByDimension(dimension.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: UnitId): UnitOfMeasure? {
        return unitDao.getById(id.value)?.toDomain()
    }
}
