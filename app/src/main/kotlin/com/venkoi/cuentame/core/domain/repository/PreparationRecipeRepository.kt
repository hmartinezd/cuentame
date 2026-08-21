package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeComponentId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipe
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeSummary
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

data class CreatePreparationRecipeCommand(
    val restaurantId: RestaurantId,
    val outputIngredientId: IngredientId,
    val name: String?,
    val standardYieldQuantity: BigDecimal?,
    val yieldUnitOptionId: IngredientUnitOptionId?,
    val notes: String?
)

data class UpdatePreparationRecipeCommand(
    val recipeId: PreparationRecipeId,
    val name: String,
    val standardYieldQuantity: BigDecimal?,
    val yieldUnitOptionId: IngredientUnitOptionId?,
    val notes: String?
)

data class SavePreparationRecipeComponentCommand(
    val recipeId: PreparationRecipeId,
    val componentId: PreparationRecipeComponentId?,
    val componentIngredientId: IngredientId,
    val unitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val sortOrder: Int,
    val notes: String?
)

interface PreparationRecipeRepository {

    fun observeRecipes(
        restaurantId: RestaurantId,
        includeArchived: Boolean = false
    ): Flow<List<PreparationRecipeSummary>>

    fun observeRecipe(
        recipeId: PreparationRecipeId
    ): Flow<PreparationRecipe?>

    suspend fun getRecipe(
        recipeId: PreparationRecipeId
    ): PreparationRecipe?

    suspend fun getRecipeForOutputIngredient(
        restaurantId: RestaurantId,
        outputIngredientId: IngredientId
    ): PreparationRecipe?

    suspend fun createDraft(
        command: CreatePreparationRecipeCommand
    ): PreparationRecipeId

    suspend fun updateDraft(
        command: UpdatePreparationRecipeCommand
    )

    suspend fun saveComponent(
        command: SavePreparationRecipeComponentCommand
    ): PreparationRecipeComponentId

    suspend fun removeComponent(
        recipeId: PreparationRecipeId,
        componentId: PreparationRecipeComponentId
    )

    suspend fun reorderComponents(
        recipeId: PreparationRecipeId,
        orderedComponentIds: List<PreparationRecipeComponentId>
    )

    suspend fun activate(
        recipeId: PreparationRecipeId
    )

    suspend fun moveToDraft(
        recipeId: PreparationRecipeId
    )

    suspend fun archive(
        recipeId: PreparationRecipeId
    )

    suspend fun restoreToDraft(
        recipeId: PreparationRecipeId
    )
}
