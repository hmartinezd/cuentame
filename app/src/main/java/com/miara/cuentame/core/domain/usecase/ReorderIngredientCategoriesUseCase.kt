package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import javax.inject.Inject

class ReorderIngredientCategoriesUseCase @Inject constructor(
    private val repository: IngredientCategoryRepository
) {
    suspend operator fun invoke(ids: List<IngredientCategoryId>) = repository.reorder(ids)
}
