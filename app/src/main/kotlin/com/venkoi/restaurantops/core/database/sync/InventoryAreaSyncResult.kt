package com.venkoi.restaurantops.core.database.sync

sealed interface InventoryAreaSyncResult {
    data class Success(val pushedCount: Int, val pulledCount: Int, val finalCursor: Long) : InventoryAreaSyncResult

    data class Conflict(
        val entityId: String,
        val currentServerVersion: Long,
        val currentChangeSeq: Long?
    ) : InventoryAreaSyncResult

    data object LocalChangesPending : InventoryAreaSyncResult
    data object RemoteFailure : InventoryAreaSyncResult
    data class InvalidOperation(val entityId: String) : InventoryAreaSyncResult
    data object ProtocolFailure : InventoryAreaSyncResult
}
