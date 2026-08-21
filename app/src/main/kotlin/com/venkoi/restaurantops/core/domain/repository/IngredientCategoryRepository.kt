package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant

interface IngredientCategoryRepository {
    fun observeActiveCategories(): Flow<List<IngredientCategory>>
    fun observeAllCategories(): Flow<List<IngredientCategory>>
    suspend fun getAllCategoriesForRestaurant(
        restaurantId: com.venkoi.restaurantops.core.common.ids.RestaurantId
    ): List<IngredientCategory> = observeAllCategories().first().filter { it.restaurantId == restaurantId }
    suspend fun getById(id: IngredientCategoryId): IngredientCategory?
    suspend fun save(category: IngredientCategory)
    suspend fun archive(id: IngredientCategoryId, at: Instant)
    suspend fun reorder(ids: List<IngredientCategoryId>)
}
