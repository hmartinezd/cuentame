package com.venkoi.restaurantops.core.database.sync

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import javax.inject.Inject

class InventoryAreaSyncPreparation @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val outboxWriter: InventoryAreaSyncOutboxWriter
) {
    suspend fun prepareUnsyncedInventoryAreas(restaurantId: String) {
        database.withTransaction {
            database.inventoryAreaDao().getAllForRestaurantSync(restaurantId).forEach { area ->
                val hasMetadata = database.syncEntityMetadataDao()
                    .get(INVENTORY_AREA_ENTITY_TYPE, area.id) != null
                val hasPending = database.syncOutboxDao()
                    .hasPending(INVENTORY_AREA_ENTITY_TYPE, area.id)
                if (!hasMetadata && !hasPending) outboxWriter.record(area)
            }
        }
    }
}
