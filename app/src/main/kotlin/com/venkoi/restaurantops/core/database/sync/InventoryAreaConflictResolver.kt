package com.venkoi.restaurantops.core.database.sync

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.SyncEntityMetadataEntity
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class InventoryAreaConflictRef(
    val restaurantId: RestaurantId,
    val entityId: String,
    val operationId: String
)

sealed interface InventoryAreaConflictResolutionResult {
    data class KeepLocalPrepared(
        val entityId: String,
        val newOperationId: String,
        val baseServerVersion: Long
    ) : InventoryAreaConflictResolutionResult

    data class CloudAccepted(
        val entityId: String,
        val serverVersion: Long,
        val changeSeq: Long
    ) : InventoryAreaConflictResolutionResult

    data object StaleConflict : InventoryAreaConflictResolutionResult
    data object RemoteFailure : InventoryAreaConflictResolutionResult
    data object ProtocolFailure : InventoryAreaConflictResolutionResult
}

@Singleton
class InventoryAreaConflictResolver @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val remote: InventoryAreaSyncRemoteDataSource,
    private val outboxWriter: InventoryAreaSyncOutboxWriter
) {
    suspend fun resolveKeepLocal(
        conflict: InventoryAreaConflictRef
    ): InventoryAreaConflictResolutionResult = resolve(conflict) { remoteRow ->
        database.withTransaction {
            if (!hasMatchingOperation(conflict)) {
                return@withTransaction InventoryAreaConflictResolutionResult.StaleConflict
            }
            val local = database.inventoryAreaDao().getById(conflict.entityId)
                ?.takeIf { it.restaurantId == conflict.restaurantId.value }
                ?: return@withTransaction InventoryAreaConflictResolutionResult.ProtocolFailure
            database.syncOutboxDao().deleteForEntity(
                conflict.restaurantId.value, INVENTORY_AREA_ENTITY_TYPE, conflict.entityId
            )
            upsertMetadata(remoteRow)
            val newOperationId = outboxWriter.recordWithBase(local, remoteRow.serverVersion)
            InventoryAreaConflictResolutionResult.KeepLocalPrepared(
                conflict.entityId, newOperationId, remoteRow.serverVersion
            )
        }
    }

    suspend fun resolveUseCloud(
        conflict: InventoryAreaConflictRef
    ): InventoryAreaConflictResolutionResult = resolve(conflict) { remoteRow ->
        database.withTransaction {
            if (!hasMatchingOperation(conflict)) {
                return@withTransaction InventoryAreaConflictResolutionResult.StaleConflict
            }
            database.syncOutboxDao().deleteForEntity(
                conflict.restaurantId.value, INVENTORY_AREA_ENTITY_TYPE, conflict.entityId
            )
            database.inventoryAreaDao().upsert(remoteRow.toEntity())
            upsertMetadata(remoteRow)
            InventoryAreaConflictResolutionResult.CloudAccepted(
                conflict.entityId, remoteRow.serverVersion, remoteRow.changeSeq
            )
        }
    }

    private suspend fun resolve(
        conflict: InventoryAreaConflictRef,
        apply: suspend (RemoteInventoryArea) -> InventoryAreaConflictResolutionResult
    ): InventoryAreaConflictResolutionResult = try {
        val remoteRow = remote.getCurrent(conflict.restaurantId, conflict.entityId).getOrElse {
            return if (it is InventoryAreaSyncProtocolException) {
                InventoryAreaConflictResolutionResult.ProtocolFailure
            } else InventoryAreaConflictResolutionResult.RemoteFailure
        }
        if (remoteRow == null || !remoteRow.isValidFor(conflict)) {
            InventoryAreaConflictResolutionResult.ProtocolFailure
        } else {
            apply(remoteRow)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        InventoryAreaConflictResolutionResult.ProtocolFailure
    }

    private suspend fun hasMatchingOperation(conflict: InventoryAreaConflictRef): Boolean {
        val operation = database.syncOutboxDao().getByOperationId(conflict.operationId) ?: return false
        return operation.restaurantId == conflict.restaurantId.value &&
            operation.entityType == INVENTORY_AREA_ENTITY_TYPE &&
            operation.entityId == conflict.entityId
    }

    private suspend fun upsertMetadata(remoteRow: RemoteInventoryArea) {
        database.syncEntityMetadataDao().upsert(
            SyncEntityMetadataEntity(
                INVENTORY_AREA_ENTITY_TYPE, remoteRow.id, remoteRow.restaurantId,
                remoteRow.serverVersion, remoteRow.changeSeq
            )
        )
    }
}

private fun RemoteInventoryArea.isValidFor(conflict: InventoryAreaConflictRef): Boolean =
    id == conflict.entityId &&
        restaurantId == conflict.restaurantId.value &&
        id.isNotBlank() && restaurantId.isNotBlank() &&
        name.isNotBlank() && normalizedName.isNotBlank() &&
        serverVersion > 0 && changeSeq > 0 &&
        createdAt >= 0 && updatedAt >= createdAt &&
        (isActive || deletedAt != null)

private fun RemoteInventoryArea.toEntity() = InventoryAreaEntity(
    id, restaurantId, name, normalizedName, sortOrder, isActive,
    createdAt, updatedAt, deletedAt
)
