package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.InventoryMovementId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityItem
import com.venkoi.restaurantops.core.model.inventory.InventoryActivityQuery
import com.venkoi.restaurantops.core.model.inventory.InventoryActivitySourceTarget
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
