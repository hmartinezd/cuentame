package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IngredientRepository {
    fun observeActiveIngredients(): Flow<List<Ingredient>>
    fun observeIngredient(id: IngredientId): Flow<Ingredient?>
    suspend fun getById(id: IngredientId): Ingredient?
    suspend fun updateIngredient(ingredient: Ingredient)
    suspend fun archive(id: IngredientId, at: Instant)

    fun observeUnitOptions(ingredientId: IngredientId): Flow<List<IngredientUnitOption>>
    suspend fun saveUnitOption(option: IngredientUnitOption)
    suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant)

    suspend fun createIngredientWithBaseOption(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption> = emptyList()
    )
}
