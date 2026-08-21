package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.IngredientCategoryId
import com.venkoi.restaurantops.core.domain.repository.IngredientCategoryRepository
import javax.inject.Inject

class ReorderIngredientCategoriesUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    suspend operator fun invoke(ids: List<IngredientCategoryId>) = repository.reorder(ids)
}
