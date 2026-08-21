package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeCost
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeCostSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PreparationCostRepository {
    fun observeRecipeCost(recipeId: PreparationRecipeId): Flow<PreparationRecipeCost?>
    fun observeRecipeCostSummaries(restaurantId: RestaurantId): Flow<List<PreparationRecipeCostSummary>>
    fun observeActivePreparationCostsByOutput(restaurantId: RestaurantId): Flow<Map<IngredientId, PreparationRecipeCost>> = flowOf(emptyMap())
}
