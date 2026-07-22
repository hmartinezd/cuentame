package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Rebuilds inventory projections (balance and cost) from historical movements.
 * 
 * Cost rebuilding algorithm:
 * 1. Initialize current total quantity and average cost to zero.
 * 2. Process movements in chronological order (effectiveAt, then createdAt, then id).
 * 3. Update area balances by adding signed quantity.
 * 4. If movement is PURCHASE or OPENING_BALANCE:
 *    a. If quantity > 0 and unit cost is present:
 *       Update average cost using the weighted average formula.
 *       Add quantity to current total quantity.
 *    b. If quantity <= 0:
 *       Subtract quantity from current total quantity (outflow at current average cost).
 * 5. If movement is WASTE, COUNT_ADJUSTMENT, or MANUAL_ADJUSTMENT:
 *    Add signed quantity to current total quantity.
 * 6. If movement is REVERSAL:
 *    Treat as an opposite movement of the original type.
 */
class RoomInventoryProjectionRebuilder @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val ingredientDao: IngredientDao,
    private val movementDao: InventoryMovementDao,
    private val projectionDao: InventoryProjectionDao,
    private val costProjectionDao: IngredientCostProjectionDao,
    private val costCalculator: WeightedAverageCostCalculator,
    private val timeProvider: TimeProvider
) {
    suspend fun rebuildForIngredient(ingredientId: IngredientId) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(ingredientId.value) ?: return@withTransaction
            val restaurantId = RestaurantId(ingredient.restaurantId)
            
            val movements = movementDao.getByIngredient(ingredientId.value).map { it.toDomain() }
            
            // Rebuild balance by area
            projectionDao.deleteForIngredient(ingredientId.value)
            val areaBalances = mutableMapOf<String, BigDecimal>()
            
            // Rebuild average cost
            costProjectionDao.deleteForIngredient(ingredientId.value)
            var currentTotalQuantity = BigDecimal.ZERO
            var currentAverageCost = BigDecimal.ZERO
            
            val updatedAt = timeProvider.now().toEpochMilli()
            
            movements.forEach { movement ->
                // Balance update
                val areaId = movement.areaId.value
                val currentAreaBalance = areaBalances.getOrDefault(areaId, BigDecimal.ZERO)
                areaBalances[areaId] = currentAreaBalance.add(movement.quantityBaseSigned)
                
                // Cost update logic
                when (movement.movementType) {
                    InventoryMovementType.PURCHASE, 
                    InventoryMovementType.OPENING_BALANCE -> {
                        val purchaseQuantity = movement.quantityBaseSigned
                        val purchaseUnitCost = movement.unitCostBaseSnapshot
                        
                        if (purchaseQuantity > BigDecimal.ZERO && purchaseUnitCost != null) {
                            currentAverageCost = costCalculator.calculate(
                                currentQuantity = currentTotalQuantity,
                                currentAverageCost = currentAverageCost,
                                purchaseQuantity = purchaseQuantity,
                                purchaseUnitCost = purchaseUnitCost
                            )
                        }
                        currentTotalQuantity = currentTotalQuantity.add(purchaseQuantity)
                    }
                    InventoryMovementType.REVERSAL -> {
                        // REVERSAL is the opposite of the original movement
                        // For simplicity in foundation, we just add the signed quantity to total.
                        // A more robust implementation would check the original movement type.
                        currentTotalQuantity = currentTotalQuantity.add(movement.quantityBaseSigned)
                    }
                    else -> {
                        // WASTE, ADJUSTMENTS
                        currentTotalQuantity = currentTotalQuantity.add(movement.quantityBaseSigned)
                    }
                }
                
                // Handle quantity becoming zero or negative
                if (currentTotalQuantity <= BigDecimal.ZERO) {
                    // Cost is maintained until the next positive purchase resets it.
                }
            }
            
            // Persist balances
            areaBalances.forEach { (areaId, quantity) ->
                projectionDao.upsert(
                    InventoryBalanceProjectionEntity(
                        restaurantId = restaurantId.value,
                        ingredientId = ingredientId.value,
                        areaId = areaId,
                        quantityBase = quantity.toPlainString(),
                        updatedAt = updatedAt
                    )
                )
            }
            
            // Persist cost
            costProjectionDao.upsert(
                IngredientCostProjectionEntity(
                    restaurantId = restaurantId.value,
                    ingredientId = ingredientId.value,
                    averageUnitCostBase = currentAverageCost.toPlainString(),
                    updatedAt = updatedAt
                )
            )
        }
    }
}
