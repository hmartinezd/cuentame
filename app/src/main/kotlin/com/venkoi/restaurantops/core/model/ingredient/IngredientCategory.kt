package com.venkoi.restaurantops.core.model.ingredient

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.time.Instant

data class IngredientCategory(
    val id: IngredientCategoryId,
    val restaurantId: RestaurantId,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
