package com.venkoi.restaurantops.core.domain.service

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class InventorySnapshot(
    val hasEffectiveHistory: Boolean,
    val areaQuantityBase: BigDecimal,
    val ingredientAverageCostBase: BigDecimal?
)

interface InventorySnapshotService {
    suspend fun calculateAt(
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): InventorySnapshot

    suspend fun calculateAreaBalancesAt(
        restaurantId: RestaurantId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): Map<IngredientId, BigDecimal>
}
