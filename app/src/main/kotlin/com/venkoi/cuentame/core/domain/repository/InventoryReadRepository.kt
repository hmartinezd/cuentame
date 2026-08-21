package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.model.inventory.InventoryBalance
import com.venkoi.cuentame.core.model.inventory.InventoryMovement
import kotlinx.coroutines.flow.Flow

interface InventoryReadRepository {
    fun observeBalances(): Flow<List<InventoryBalance>>
    fun observeIngredientBalances(ingredientId: IngredientId): Flow<List<InventoryBalance>>
    fun observeMovementHistory(ingredientId: IngredientId): Flow<List<InventoryMovement>>
}
