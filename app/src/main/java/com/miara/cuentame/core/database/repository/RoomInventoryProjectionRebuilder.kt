package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomInventoryProjectionRebuilder @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val movementDao: InventoryMovementDao,
    private val projectionDao: InventoryProjectionDao,
    private val costProjectionDao: IngredientCostProjectionDao,
    private val costCalculator: WeightedAverageCostCalculator
) {
    suspend fun rebuildForIngredient(restaurantId: RestaurantId, ingredientId: IngredientId) {
        database.withTransaction {
            val movements = movementDao.getByIngredient(ingredientId.value).map { it.toDomain() }
            
            // Rebuild balance by area
            projectionDao.deleteForIngredient(ingredientId.value)
            val areaBalances = mutableMapOf<String, BigDecimal>()
            
            // Rebuild average cost
            costProjectionDao.deleteForIngredient(ingredientId.value)
            var currentTotalQuantity = BigDecimal.ZERO
            var currentAverageCost = BigDecimal.ZERO
            
            val now = Instant.now().toEpochMilli()
            
            movements.forEach { movement ->
                // Balance
                val areaId = movement.areaId.value
                val currentAreaBalance = areaBalances.getOrDefault(areaId, BigDecimal.ZERO)
                areaBalances[areaId] = currentAreaBalance.add(movement.quantityBaseSigned)
                
                // Cost (only for purchases or opening balance if unit cost is present)
                if (movement.movementType == InventoryMovementType.PURCHASE || 
                    movement.movementType == InventoryMovementType.OPENING_BALANCE) {
                    
                    val purchaseQuantity = movement.quantityBaseSigned
                    val purchaseUnitCost = movement.unitCostBaseSnapshot
                    
                    if (purchaseQuantity > BigDecimal.ZERO && purchaseUnitCost != null) {
                        currentAverageCost = costCalculator.calculate(
                            currentQuantity = currentTotalQuantity,
                            currentAverageCost = currentAverageCost,
                            purchaseQuantity = purchaseQuantity,
                            purchaseUnitCost = purchaseUnitCost
                        )
                        currentTotalQuantity = currentTotalQuantity.add(purchaseQuantity)
                    }
                } else {
                    // For other movements, just update total quantity if needed
                    // Actually average cost is usually only affected by inflows with costs.
                    currentTotalQuantity = currentTotalQuantity.add(movement.quantityBaseSigned)
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
                        updatedAt = now
                    )
                )
            }
            
            // Persist cost
            costProjectionDao.upsert(
                IngredientCostProjectionEntity(
                    restaurantId = restaurantId.value,
                    ingredientId = ingredientId.value,
                    averageUnitCostBase = currentAverageCost.toPlainString(),
                    updatedAt = now
                )
            )
        }
    }
}
