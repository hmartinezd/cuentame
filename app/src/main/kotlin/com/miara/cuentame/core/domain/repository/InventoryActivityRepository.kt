package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.model.inventory.InventoryActivityItem
import com.miara.cuentame.core.model.inventory.InventoryActivityQuery
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import kotlinx.coroutines.flow.Flow

interface InventoryActivityRepository {
    fun observeActivity(
        query: InventoryActivityQuery
    ): Flow<List<InventoryActivityItem>>

    suspend fun getActivityItem(
        movementId: InventoryMovementId
    ): InventoryActivityItem?

    fun resolveSourceTarget(
        item: InventoryActivityItem
    ): InventoryActivitySourceTarget
}
