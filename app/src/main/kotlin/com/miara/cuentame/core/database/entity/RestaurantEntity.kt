package com.miara.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currencyCode: String,
    val localeTag: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)
