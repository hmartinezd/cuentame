package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["operationId"], unique = true),
        Index(value = ["restaurantId", "entityType", "localSequence"]),
        Index(value = ["entityType", "entityId", "localSequence"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val localSequence: Long = 0,
    val operationId: String,
    val restaurantId: String,
    val entityType: String,
    val entityId: String,
    val baseServerVersion: Long,
    val payloadJson: String,
    val createdAt: Long
)
