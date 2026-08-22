package com.venkoi.restaurantops.core.database.sync

import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.dao.SyncEntityMetadataDao
import com.venkoi.restaurantops.core.database.dao.SyncOutboxDao
import com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity
import com.venkoi.restaurantops.core.database.entity.SyncOutboxEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class IngredientCategorySyncOutboxWriter @Inject constructor(
    private val metadataDao: SyncEntityMetadataDao,
    private val outboxDao: SyncOutboxDao,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val json: Json
) {
    /** Must be called inside the transaction that persists [category]. */
    suspend fun record(category: IngredientCategoryEntity) {
        val baseVersion = metadataDao.get(INGREDIENT_CATEGORY_ENTITY_TYPE, category.id)
            ?.serverVersion ?: 0L
        recordWithBase(category, baseVersion)
    }

    suspend fun recordWithBase(
        category: IngredientCategoryEntity,
        baseServerVersion: Long
    ): String {
        val payload = IngredientCategorySyncPayload(
            category.id,
            category.restaurantId,
            category.name,
            category.normalizedName,
            category.sortOrder,
            category.isActive,
            category.createdAt,
            category.updatedAt,
            category.deletedAt
        )
        val operationId = idGenerator.newId()
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                restaurantId = category.restaurantId,
                entityType = INGREDIENT_CATEGORY_ENTITY_TYPE,
                entityId = category.id,
                baseServerVersion = baseServerVersion,
                payloadJson = json.encodeToString(payload),
                createdAt = timeProvider.now().toEpochMilli()
            )
        )
        return operationId
    }
}
