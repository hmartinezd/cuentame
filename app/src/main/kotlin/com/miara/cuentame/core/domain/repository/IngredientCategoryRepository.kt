package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IngredientCategoryRepository {
    fun observeActiveCategories(): Flow<List<IngredientCategory>>
    fun observeAllCategories(): Flow<List<IngredientCategory>>
    suspend fun getById(id: IngredientCategoryId): IngredientCategory?
    suspend fun save(category: IngredientCategory)
    suspend fun archive(id: IngredientCategoryId, at: Instant)
    suspend fun reorder(ids: List<IngredientCategoryId>)
}
