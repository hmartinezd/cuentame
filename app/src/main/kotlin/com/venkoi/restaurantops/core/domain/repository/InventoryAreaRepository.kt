package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
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
