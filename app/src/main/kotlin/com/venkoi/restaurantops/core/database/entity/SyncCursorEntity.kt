package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["restaurantId", "entityType"]
)
data class SyncCursorEntity(
    val restaurantId: String,
    val entityType: String,
    val changeSeq: Long
)
