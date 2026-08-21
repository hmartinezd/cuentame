package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
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
