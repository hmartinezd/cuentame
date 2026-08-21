package com.venkoi.restaurantops.core.model.inventory

import com.venkoi.restaurantops.core.common.ids.UnitId
import java.math.BigDecimal

data class UnitOfMeasure(
    val id: UnitId,
    val name: String,
    val symbol: String,
    val dimension: UnitDimension,
    val factorToCanonical: BigDecimal,
    val isSystem: Boolean,
    val sortOrder: Int
)
