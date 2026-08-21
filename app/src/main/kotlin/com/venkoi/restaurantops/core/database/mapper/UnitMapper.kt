package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.parsePersistedEnum
import com.venkoi.restaurantops.core.common.ids.UnitId
import com.venkoi.restaurantops.core.database.entity.UnitEntity
import com.venkoi.restaurantops.core.model.inventory.UnitDimension
import com.venkoi.restaurantops.core.model.inventory.UnitOfMeasure

fun UnitEntity.toDomain(): UnitOfMeasure = UnitOfMeasure(
    id = UnitId(id),
    name = name,
    symbol = symbol,
    dimension = parsePersistedEnum(dimension, UnitDimension.UNKNOWN),
    factorToCanonical = factorToCanonical,
    isSystem = isSystem,
    sortOrder = sortOrder
)

fun UnitOfMeasure.toEntity(): UnitEntity = UnitEntity(
    id = id.value,
    name = name,
    symbol = symbol,
    dimension = dimension.name,
    factorToCanonical = factorToCanonical,
    isSystem = isSystem,
    sortOrder = sortOrder
)
