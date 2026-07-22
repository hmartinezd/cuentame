package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveIngredientsUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    operator fun invoke(restaurantId: RestaurantId, includeArchived: Boolean = false): Flow<List<Ingredient>> = 
        repository.observeIngredients(restaurantId, includeArchived)
}

class GetIngredientDetailUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(id: IngredientId): Ingredient? = repository.getById(id)
}

class CreateIngredientUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption> = emptyList()
    ) = repository.createIngredientWithBaseOption(ingredient, baseOption, additionalOptions)
}

class UpdateIngredientUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(ingredient: Ingredient) = repository.updateIngredient(ingredient)
}

class ArchiveIngredientUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(id: IngredientId, at: Instant) = repository.archive(id, at)
}
