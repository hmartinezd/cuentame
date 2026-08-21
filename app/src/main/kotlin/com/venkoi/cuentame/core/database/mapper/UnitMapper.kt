package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.UnitId
import com.venkoi.cuentame.core.database.entity.UnitEntity
import com.venkoi.cuentame.core.model.inventory.UnitDimension
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure

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
