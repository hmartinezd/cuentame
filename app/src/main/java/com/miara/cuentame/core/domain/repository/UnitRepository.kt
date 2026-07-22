package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import kotlinx.coroutines.flow.Flow

interface UnitRepository {
    fun observeAll(): Flow<List<UnitOfMeasure>>
    fun observeByDimension(dimension: UnitDimension): Flow<List<UnitOfMeasure>>
    suspend fun getById(id: UnitId): UnitOfMeasure?
}
