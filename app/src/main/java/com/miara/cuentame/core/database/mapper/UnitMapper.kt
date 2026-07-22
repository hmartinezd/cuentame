package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import java.math.BigDecimal

fun UnitEntity.toDomain(): UnitOfMeasure = UnitOfMeasure(
    id = UnitId(id),
    name = name,
    symbol = symbol,
    dimension = UnitDimension.valueOf(dimension),
    factorToCanonical = BigDecimal(factorToCanonical),
    isSystem = isSystem,
    sortOrder = sortOrder
)

fun UnitOfMeasure.toEntity(): UnitEntity = UnitEntity(
    id = id.value,
    name = name,
    symbol = symbol,
    dimension = dimension.name,
    factorToCanonical = factorToCanonical.toPlainString(),
    isSystem = isSystem,
    sortOrder = sortOrder
)
