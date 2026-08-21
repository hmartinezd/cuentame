package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientCategoryId
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.UnitId
import com.venkoi.cuentame.core.model.ingredient.Ingredient
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class UpdateIngredientCommand(
    val ingredientId: IngredientId,
    val name: String,
    val categoryId: IngredientCategoryId?,
    val parLevelBase: BigDecimal? = null,
    val reorderPointBase: BigDecimal? = null,
    val defaultAreaId: InventoryAreaId? = null
)

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
    suspend fun getIngredients(restaurantId: RestaurantId, includeArchived: Boolean): List<Ingredient>
    fun observeIngredient(id: IngredientId): Flow<Ingredient?>
    suspend fun getById(id: IngredientId): Ingredient?
    suspend fun getUnitOption(id: IngredientUnitOptionId): IngredientUnitOption?
    suspend fun updateIngredient(command: UpdateIngredientCommand)
    suspend fun assignDefaultArea(ingredientIds: List<IngredientId>, areaId: InventoryAreaId) {
        throw UnsupportedOperationException("Default-area assignment is not implemented")
    }
    suspend fun archive(id: IngredientId, at: Instant)

    fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean = false): Flow<List<IngredientUnitOption>>
    suspend fun getUnitOptions(ingredientId: IngredientId, includeArchived: Boolean = false): List<IngredientUnitOption>
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
