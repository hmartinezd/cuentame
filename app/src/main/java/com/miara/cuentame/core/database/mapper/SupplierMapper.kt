package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.database.entity.SupplierEntity
import com.miara.cuentame.core.model.supplier.Supplier
import java.time.Instant

fun SupplierEntity.toDomain(): Supplier = Supplier(
    id = SupplierId(id),
    restaurantId = RestaurantId(restaurantId),
    name = name,
    normalizedName = normalizedName,
    phone = phone,
    email = email,
    notes = notes,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) }
)

fun Supplier.toEntity(): SupplierEntity = SupplierEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    name = name,
    normalizedName = normalizedName,
    phone = phone,
    email = email,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli()
)
