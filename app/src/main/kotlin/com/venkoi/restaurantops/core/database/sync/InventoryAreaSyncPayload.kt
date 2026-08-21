package com.venkoi.restaurantops.core.database.sync

import kotlinx.serialization.Serializable

@Serializable
internal data class InventoryAreaSyncPayload(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

internal const val INVENTORY_AREA_ENTITY_TYPE = "INVENTORY_AREA"
