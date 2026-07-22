package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.model.restaurant.Restaurant
import java.time.Instant

fun RestaurantEntity.toDomain(): Restaurant = Restaurant(
    id = RestaurantId(id),
    name = name,
    currencyCode = currencyCode,
    localeTag = localeTag,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
)

fun Restaurant.toEntity(): RestaurantEntity = RestaurantEntity(
    id = id.value,
    name = name,
    currencyCode = currencyCode,
    localeTag = localeTag,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli()
)
