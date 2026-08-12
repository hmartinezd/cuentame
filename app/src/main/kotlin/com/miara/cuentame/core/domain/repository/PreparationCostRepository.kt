package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.ingredient.PreparationRecipeCost
import com.miara.cuentame.core.model.ingredient.PreparationRecipeCostSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PreparationCostRepository {
    fun observeRecipeCost(recipeId: PreparationRecipeId): Flow<PreparationRecipeCost?>
    fun observeRecipeCostSummaries(restaurantId: RestaurantId): Flow<List<PreparationRecipeCostSummary>>
    fun observeActivePreparationCostsByOutput(restaurantId: RestaurantId): Flow<Map<IngredientId, PreparationRecipeCost>> = flowOf(emptyMap())
}
