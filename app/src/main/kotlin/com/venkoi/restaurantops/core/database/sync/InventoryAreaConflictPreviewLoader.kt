package com.venkoi.restaurantops.core.database.sync

import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.domain.repository.InventoryAreaRepository
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import java.time.Instant
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class InventoryAreaConflictPreview(
    val local: InventoryArea,
    val cloud: InventoryArea
)

sealed interface InventoryAreaConflictPreviewResult {
    data class Available(val preview: InventoryAreaConflictPreview) : InventoryAreaConflictPreviewResult
    data class RemoteFailure(val local: InventoryArea) : InventoryAreaConflictPreviewResult
    data class Unavailable(val local: InventoryArea?) : InventoryAreaConflictPreviewResult
}

class InventoryAreaConflictPreviewLoader @Inject constructor(
    private val areas: InventoryAreaRepository,
    private val remote: InventoryAreaSyncRemoteDataSource
) {
    suspend fun load(conflict: InventoryAreaConflictRef): InventoryAreaConflictPreviewResult = try {
        val local = areas.getById(InventoryAreaId(conflict.entityId))
            ?.takeIf { it.restaurantId == conflict.restaurantId }
            ?: return InventoryAreaConflictPreviewResult.Unavailable(local = null)
        val cloud = remote.getCurrent(conflict.restaurantId, conflict.entityId).getOrElse {
            return if (it is InventoryAreaSyncProtocolException) {
                InventoryAreaConflictPreviewResult.Unavailable(local)
            } else InventoryAreaConflictPreviewResult.RemoteFailure(local)
        } ?: return InventoryAreaConflictPreviewResult.Unavailable(local)
        if (!cloud.isValidPreview(conflict)) return InventoryAreaConflictPreviewResult.Unavailable(local)
        InventoryAreaConflictPreviewResult.Available(
            InventoryAreaConflictPreview(
                local,
                InventoryArea(
                    InventoryAreaId(cloud.id), conflict.restaurantId, cloud.name, cloud.normalizedName,
                    cloud.sortOrder, cloud.isActive, Instant.ofEpochMilli(cloud.createdAt),
                    Instant.ofEpochMilli(cloud.updatedAt), cloud.deletedAt?.let(Instant::ofEpochMilli)
                )
            )
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        InventoryAreaConflictPreviewResult.Unavailable(local = null)
    }
}

private fun RemoteInventoryArea.isValidPreview(conflict: InventoryAreaConflictRef): Boolean =
    id == conflict.entityId && restaurantId == conflict.restaurantId.value &&
        name.isNotBlank() && normalizedName.isNotBlank() &&
        serverVersion > 0 && changeSeq > 0 && createdAt >= 0 && updatedAt >= createdAt &&
        isActive == (deletedAt == null)
