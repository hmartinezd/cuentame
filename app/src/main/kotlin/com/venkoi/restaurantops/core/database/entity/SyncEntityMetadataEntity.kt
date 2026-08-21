package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_entity_metadata",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["restaurantId", "entityType"])]
)
data class SyncEntityMetadataEntity(
    val entityType: String,
    val entityId: String,
    val restaurantId: String,
    val serverVersion: Long,
    val changeSeq: Long
)
