package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.inventory.InventoryBalance
import com.miara.cuentame.core.model.inventory.InventoryMovement
import kotlinx.coroutines.flow.Flow

interface InventoryReadRepository {
    fun observeBalances(): Flow<List<InventoryBalance>>
    fun observeIngredientBalances(ingredientId: IngredientId): Flow<List<InventoryBalance>>
    fun observeMovementHistory(ingredientId: IngredientId): Flow<List<InventoryMovement>>
}
