package com.miara.cuentame.core.model.ingredient

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class IngredientCost(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val averageUnitCostBase: BigDecimal,
    val updatedAt: Instant
)
