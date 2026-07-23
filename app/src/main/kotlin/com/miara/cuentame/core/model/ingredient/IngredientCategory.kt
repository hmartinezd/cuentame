package com.miara.cuentame.core.model.ingredient

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.RestaurantId
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
