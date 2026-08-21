package com.venkoi.restaurantops.core.database.sync

import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.dao.SyncEntityMetadataDao
import com.venkoi.restaurantops.core.database.dao.SyncOutboxDao
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class InventoryAreaSyncOutboxWriter @Inject constructor(
    private val metadataDao: SyncEntityMetadataDao,
    private val outboxDao: SyncOutboxDao,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val json: Json
) {
    /** Must be called inside the transaction that persists [area]. */
    suspend fun record(area: InventoryAreaEntity) {
        val baseVersion = metadataDao.get(INVENTORY_AREA_ENTITY_TYPE, area.id)?.serverVersion ?: 0L
        val payload = InventoryAreaSyncPayload(
            area.id, area.restaurantId, area.name, area.normalizedName, area.sortOrder,
            area.isActive, area.createdAt, area.updatedAt, area.deletedAt
        )
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = idGenerator.newId(),
                restaurantId = area.restaurantId,
                entityType = INVENTORY_AREA_ENTITY_TYPE,
                entityId = area.id,
                baseServerVersion = baseVersion,
                payloadJson = json.encodeToString(payload),
                createdAt = timeProvider.now().toEpochMilli()
            )
        )
    }
}
