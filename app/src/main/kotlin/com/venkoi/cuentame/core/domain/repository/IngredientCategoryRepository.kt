package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientCategoryId
import com.venkoi.cuentame.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant

interface IngredientCategoryRepository {
    fun observeActiveCategories(): Flow<List<IngredientCategory>>
    fun observeAllCategories(): Flow<List<IngredientCategory>>
    suspend fun getAllCategoriesForRestaurant(
        restaurantId: com.venkoi.cuentame.core.common.ids.RestaurantId
    ): List<IngredientCategory> = observeAllCategories().first().filter { it.restaurantId == restaurantId }
    suspend fun getById(id: IngredientCategoryId): IngredientCategory?
    suspend fun save(category: IngredientCategory)
    suspend fun archive(id: IngredientCategoryId, at: Instant)
    suspend fun reorder(ids: List<IngredientCategoryId>)
}
