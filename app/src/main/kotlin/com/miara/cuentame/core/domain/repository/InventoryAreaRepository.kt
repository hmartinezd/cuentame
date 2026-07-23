package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.model.inventory.InventoryArea
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface InventoryAreaRepository {
    fun observeActiveAreas(): Flow<List<InventoryArea>>
    fun observeAllAreas(): Flow<List<InventoryArea>>
    suspend fun getById(id: InventoryAreaId): InventoryArea?
    suspend fun save(area: InventoryArea)
    suspend fun archive(id: InventoryAreaId, at: Instant)
    suspend fun reorder(ids: List<InventoryAreaId>)
}
