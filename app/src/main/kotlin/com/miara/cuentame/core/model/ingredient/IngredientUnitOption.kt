package com.miara.cuentame.core.model.ingredient

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.UnitId
import java.math.BigDecimal
import java.time.Instant

data class IngredientUnitOption(
    val id: IngredientUnitOptionId,
    val ingredientId: IngredientId,
    val displayName: String,
    val shortLabel: String,
    val standardUnitId: UnitId?,
    val factorToBase: BigDecimal,
    val isBase: Boolean,
    val isDefaultCount: Boolean,
    val isDefaultPurchase: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
