package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveIngredientUnitOptionsUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    operator fun invoke(ingredientId: IngredientId): Flow<List<IngredientUnitOption>> =
        repository.observeUnitOptions(ingredientId)
}

class SaveIngredientUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(option: IngredientUnitOption) = repository.saveUnitOption(option)
}

class ArchiveIngredientUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(id: IngredientUnitOptionId, at: Instant) =
        repository.archiveUnitOption(id, at)
}
