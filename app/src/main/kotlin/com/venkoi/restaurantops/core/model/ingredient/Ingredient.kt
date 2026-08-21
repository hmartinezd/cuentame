package com.venkoi.restaurantops.core.model.ingredient

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.UnitId
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
    val deletedAt: Instant? = null,
    val parLevelBase: BigDecimal? = null
)
