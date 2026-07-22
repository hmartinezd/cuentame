package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class AddStandardUnitOptionCommand(
    val ingredientId: IngredientId,
    val standardUnitId: UnitId,
    val isDefaultCount: Boolean = false,
    val isDefaultPurchase: Boolean = false
)

data class AddPackageUnitOptionCommand(
    val ingredientId: IngredientId,
    val displayName: String,
    val factorToBase: BigDecimal,
    val isDefaultCount: Boolean = false,
    val isDefaultPurchase: Boolean = false
)

data class UpdatePackageUnitOptionCommand(
    val optionId: IngredientUnitOptionId,
    val displayName: String,
    val factorToBase: BigDecimal
)

interface IngredientRepository {
    fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>>
    fun observeIngredient(id: IngredientId): Flow<Ingredient?>
    suspend fun getById(id: IngredientId): Ingredient?
    suspend fun updateIngredient(ingredient: Ingredient)
    suspend fun archive(id: IngredientId, at: Instant)

    fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean = false): Flow<List<IngredientUnitOption>>
    suspend fun addStandardUnitOption(command: AddStandardUnitOptionCommand)
    suspend fun addPackageUnitOption(command: AddPackageUnitOptionCommand)
    suspend fun updatePackageUnitOption(command: UpdatePackageUnitOptionCommand)
    suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId)
    suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId)
    suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant)

    suspend fun createIngredientWithBaseOption(
        ingredient: Ingredient,
        baseOption: IngredientUnitOption,
        additionalOptions: List<IngredientUnitOption> = emptyList()
    )
}
