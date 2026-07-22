package com.miara.cuentame.core.model.ingredient

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import java.math.BigDecimal
import java.time.Instant

data class Ingredient(
    val id: IngredientId,
    val restaurantId: RestaurantId,
    val name: String,
    val normalizedName: String,
    val categoryId: IngredientCategoryId? = null,
    val baseUnitId: UnitId,
    val defaultAreaId: InventoryAreaId? = null,
    val sku: String? = null,
    val notes: String? = null,
    val reorderPointBase: BigDecimal? = null,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
