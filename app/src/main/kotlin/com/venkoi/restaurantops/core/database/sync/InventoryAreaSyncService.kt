package com.venkoi.restaurantops.core.database.sync

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.SyncCursorEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryAreaSyncService @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val preparation: InventoryAreaSyncPreparation,
    private val remote: InventoryAreaSyncRemoteDataSource
) {
    suspend fun sync(restaurantId: RestaurantId): InventoryAreaSyncResult = try {
        preparation.prepareUnsyncedInventoryAreas(restaurantId.value)
        var pushedCount = 0

        while (true) {
            val operation = database.syncOutboxDao()
                .getPending(restaurantId.value, INVENTORY_AREA_ENTITY_TYPE, 1)
                .singleOrNull() ?: break
            val request = InventoryAreaRemoteOperation(
                operation.operationId, operation.restaurantId, operation.entityId,
                operation.baseServerVersion, operation.payloadJson
            )
            val response = remote.apply(request).getOrElse {
                return if (it is InventoryAreaSyncProtocolException) {
                    InventoryAreaSyncResult.ProtocolFailure
                } else InventoryAreaSyncResult.RemoteFailure
            }
            if (response.entityId != operation.entityId) return InventoryAreaSyncResult.ProtocolFailure

            when (response) {
                is InventoryAreaRemoteApplyResult.Applied -> {
                    acknowledge(operation.localSequence, operation.operationId, operation.restaurantId,
                        operation.entityId, response.serverVersion, response.changeSeq)
                    pushedCount++
                }
                is InventoryAreaRemoteApplyResult.AlreadyApplied -> {
                    acknowledge(operation.localSequence, operation.operationId, operation.restaurantId,
                        operation.entityId, response.serverVersion, response.changeSeq)
                    pushedCount++
                }
                is InventoryAreaRemoteApplyResult.Conflict -> return InventoryAreaSyncResult.Conflict(
                    response.entityId, operation.operationId,
                    response.currentServerVersion, response.currentChangeSeq
                )
                is InventoryAreaRemoteApplyResult.InvalidOperation ->
                    return InventoryAreaSyncResult.InvalidOperation(response.entityId)
            }
        }

        if (database.syncOutboxDao().hasPendingForRestaurant(restaurantId.value, INVENTORY_AREA_ENTITY_TYPE)) {
            return InventoryAreaSyncResult.LocalChangesPending
        }

        var cursor = database.syncCursorDao().get(restaurantId.value, INVENTORY_AREA_ENTITY_TYPE)?.changeSeq ?: 0L
        var pulledCount = 0
        while (true) {
            val requestedCursor = cursor
            val page = remote.pull(restaurantId, requestedCursor, PAGE_SIZE).getOrElse {
                return if (it is InventoryAreaSyncProtocolException) {
                    InventoryAreaSyncResult.ProtocolFailure
                } else InventoryAreaSyncResult.RemoteFailure
            }
            if (!isValidPage(page, restaurantId.value, requestedCursor)) {
                return InventoryAreaSyncResult.ProtocolFailure
            }
            if (page.isEmpty()) break

            val applied = applyPage(restaurantId.value, page)
            if (!applied) return InventoryAreaSyncResult.LocalChangesPending
            cursor = page.last().changeSeq
            pulledCount += page.size
            if (page.size < PAGE_SIZE) break
        }
        InventoryAreaSyncResult.Success(pushedCount, pulledCount, cursor)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        InventoryAreaSyncResult.ProtocolFailure
    }

    private suspend fun acknowledge(
        localSequence: Long,
        operationId: String,
        restaurantId: String,
        entityId: String,
        serverVersion: Long,
        changeSeq: Long
    ) {
        database.withTransaction {
            val current = requireNotNull(database.syncOutboxDao().getByLocalSequence(localSequence))
            check(current.operationId == operationId && current.entityId == entityId &&
                current.restaurantId == restaurantId && current.entityType == INVENTORY_AREA_ENTITY_TYPE)
            database.syncEntityMetadataDao().upsert(
                SyncEntityMetadataEntity(
                    INVENTORY_AREA_ENTITY_TYPE, entityId, restaurantId, serverVersion, changeSeq
                )
            )
            check(database.syncOutboxDao().deleteByOperationId(operationId) == 1)
            database.syncOutboxDao().updateBaseVersionForLaterEntityOperations(
                INVENTORY_AREA_ENTITY_TYPE, entityId, localSequence, serverVersion
            )
        }
    }

    private fun isValidPage(page: List<RemoteInventoryArea>, restaurantId: String, cursor: Long): Boolean {
        var previous = cursor
        for (row in page) {
            if (row.restaurantId != restaurantId || row.changeSeq <= previous) return false
            previous = row.changeSeq
        }
        return true
    }

    /** Returns false when local work appeared before this page's write transaction. */
    private suspend fun applyPage(restaurantId: String, page: List<RemoteInventoryArea>): Boolean =
        database.withTransaction {
            if (database.syncOutboxDao().hasPendingForRestaurant(restaurantId, INVENTORY_AREA_ENTITY_TYPE)) {
                return@withTransaction false
            }
            page.forEach { row ->
                database.inventoryAreaDao().upsert(
                    InventoryAreaEntity(
                        row.id, row.restaurantId, row.name, row.normalizedName, row.sortOrder,
                        row.isActive, row.createdAt, row.updatedAt, row.deletedAt
                    )
                )
                database.syncEntityMetadataDao().upsert(
                    SyncEntityMetadataEntity(
                        INVENTORY_AREA_ENTITY_TYPE, row.id, row.restaurantId,
                        row.serverVersion, row.changeSeq
                    )
                )
            }
            database.syncCursorDao().upsert(
                SyncCursorEntity(restaurantId, INVENTORY_AREA_ENTITY_TYPE, page.last().changeSeq)
            )
            true
        }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
