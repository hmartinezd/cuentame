package com.venkoi.restaurantops.core.database.sync

import com.venkoi.restaurantops.core.common.ids.RestaurantId

interface InventoryAreaSyncRemoteDataSource {
    suspend fun apply(operation: InventoryAreaRemoteOperation): Result<InventoryAreaRemoteApplyResult>

    suspend fun pull(
        restaurantId: RestaurantId,
        afterChangeSeq: Long,
        limit: Int
    ): Result<List<RemoteInventoryArea>>

    suspend fun getCurrent(
        restaurantId: RestaurantId,
        entityId: String
    ): Result<RemoteInventoryArea?>
}

data class InventoryAreaRemoteOperation(
    val operationId: String,
    val restaurantId: String,
    val entityId: String,
    val baseServerVersion: Long,
    val payloadJson: String
)

sealed interface InventoryAreaRemoteApplyResult {
    val entityId: String

    data class Applied(
        override val entityId: String,
        val serverVersion: Long,
        val changeSeq: Long
    ) : InventoryAreaRemoteApplyResult

    data class AlreadyApplied(
        override val entityId: String,
        val serverVersion: Long,
        val changeSeq: Long
    ) : InventoryAreaRemoteApplyResult

    data class Conflict(
        override val entityId: String,
        val currentServerVersion: Long,
        val currentChangeSeq: Long?
    ) : InventoryAreaRemoteApplyResult

    data class InvalidOperation(override val entityId: String) : InventoryAreaRemoteApplyResult
}

data class RemoteInventoryArea(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val serverVersion: Long,
    val changeSeq: Long
)

/** A valid cloud response could not be interpreted according to the sync protocol. */
class InventoryAreaSyncProtocolException : Exception()
