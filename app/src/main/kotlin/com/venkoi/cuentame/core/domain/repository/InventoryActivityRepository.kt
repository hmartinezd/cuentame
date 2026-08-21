package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.InventoryMovementId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.model.inventory.InventoryActivityItem
import com.venkoi.cuentame.core.model.inventory.InventoryActivityQuery
import com.venkoi.cuentame.core.model.inventory.InventoryActivitySourceTarget
import kotlinx.coroutines.flow.Flow

interface InventoryActivityRepository {
    fun observeActivity(
        query: InventoryActivityQuery
    ): Flow<List<InventoryActivityItem>>

    suspend fun getActivityItem(
        restaurantId: RestaurantId,
        movementId: InventoryMovementId
    ): InventoryActivityItem?

    fun resolveSourceTarget(
        item: InventoryActivityItem
    ): InventoryActivitySourceTarget
}
