package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.domain.repository.IngredientCategoryRepository
import com.venkoi.restaurantops.core.model.ingredient.IngredientCategory
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveIngredientCategoriesUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    operator fun invoke(activeOnly: Boolean = true): Flow<List<IngredientCategory>> =
        if (activeOnly) repository.observeActiveCategories() else repository.observeAllCategories()
}

class CreateIngredientCategoryUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    suspend operator fun invoke(category: IngredientCategory) = repository.save(category)
}

class UpdateIngredientCategoryUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    suspend operator fun invoke(category: IngredientCategory) = repository.save(category)
}

class ArchiveIngredientCategoryUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    suspend operator fun invoke(id: IngredientCategoryId, at: Instant) = repository.archive(id, at)
}
