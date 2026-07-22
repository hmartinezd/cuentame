package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.repository.InventoryReadRepository
import com.miara.cuentame.core.model.inventory.InventoryBalance
import com.miara.cuentame.core.model.inventory.InventoryMovement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomInventoryReadRepository @Inject constructor(
    private val projectionDao: InventoryProjectionDao,
    private val movementDao: InventoryMovementDao
) : InventoryReadRepository {
    override fun observeBalances(): Flow<List<InventoryBalance>> {
        return projectionDao.observeAllBalances().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeIngredientBalances(ingredientId: IngredientId): Flow<List<InventoryBalance>> {
        return projectionDao.observeBalancesForIngredient(ingredientId.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeMovementHistory(ingredientId: IngredientId): Flow<List<InventoryMovement>> {
        return movementDao.observeByIngredient(ingredientId.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
