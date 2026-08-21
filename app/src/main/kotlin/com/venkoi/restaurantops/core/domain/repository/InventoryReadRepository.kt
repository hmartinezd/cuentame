package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.model.inventory.InventoryBalance
import com.venkoi.restaurantops.core.model.inventory.InventoryMovement
import kotlinx.coroutines.flow.Flow

interface InventoryReadRepository {
    fun observeBalances(): Flow<List<InventoryBalance>>
    fun observeIngredientBalances(ingredientId: IngredientId): Flow<List<InventoryBalance>>
    fun observeMovementHistory(ingredientId: IngredientId): Flow<List<InventoryMovement>>
}
