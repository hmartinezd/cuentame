package com.venkoi.restaurantops.core.database.sync

import kotlinx.serialization.Serializable

@Serializable
internal data class IngredientCategorySyncPayload(
    val id: String,
    val restaurantId: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

internal const val INGREDIENT_CATEGORY_ENTITY_TYPE = "INGREDIENT_CATEGORY"
