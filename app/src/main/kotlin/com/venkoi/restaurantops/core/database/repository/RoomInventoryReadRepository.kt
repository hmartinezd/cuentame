package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.database.dao.InventoryMovementDao
import com.venkoi.restaurantops.core.database.dao.InventoryProjectionDao
import com.venkoi.restaurantops.core.database.mapper.toDomain
import com.venkoi.restaurantops.core.domain.repository.InventoryReadRepository
import com.venkoi.restaurantops.core.model.inventory.InventoryBalance
import com.venkoi.restaurantops.core.model.inventory.InventoryMovement
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
