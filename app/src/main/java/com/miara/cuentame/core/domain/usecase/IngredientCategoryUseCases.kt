package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.model.ingredient.IngredientCategory
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
