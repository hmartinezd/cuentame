package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.PreparationRecipeId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeCost
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeCostSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PreparationCostRepository {
    fun observeRecipeCost(recipeId: PreparationRecipeId): Flow<PreparationRecipeCost?>
    fun observeRecipeCostSummaries(restaurantId: RestaurantId): Flow<List<PreparationRecipeCostSummary>>
    fun observeActivePreparationCostsByOutput(restaurantId: RestaurantId): Flow<Map<IngredientId, PreparationRecipeCost>> = flowOf(emptyMap())
}
