package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomInventorySnapshotService @Inject constructor(
    private val movementDao: InventoryMovementDao,
    private val costCalculator: WeightedAverageCostCalculator
) : InventorySnapshotService {

    override suspend fun calculateAt(
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): InventorySnapshot {
        val allMovements = movementDao.getByIngredient(ingredientId.value)
            .map { it.toDomain() }
            .filter { it.effectiveAt <= effectiveAt }
        
        val reversedMovementIds = allMovements
            .mapNotNull { it.reversalOfMovementId?.value }
            .toSet()
        
        val effectiveMovements = allMovements.filter { movement ->
            movement.movementType != InventoryMovementType.REVERSAL && 
            !reversedMovementIds.contains(movement.id.value)
        }
        
        var areaQuantity = BigDecimal.ZERO
        var totalQuantity = BigDecimal.ZERO
        var averageCost = BigDecimal.ZERO
        var hasHistoryInArea = false

        effectiveMovements.forEach { movement ->
            val isTargetArea = movement.areaId == areaId
            if (isTargetArea) {
                areaQuantity = areaQuantity.add(movement.quantityBaseSigned)
                hasHistoryInArea = true
            }

            when (movement.movementType) {
                InventoryMovementType.PURCHASE, 
                InventoryMovementType.OPENING_BALANCE -> {
                    val incomingQuantity = movement.quantityBaseSigned
                    val incomingUnitCost = movement.unitCostBaseSnapshot
                    
                    if (incomingQuantity > BigDecimal.ZERO && incomingUnitCost != null) {
                        averageCost = costCalculator.calculate(
                            currentQuantity = totalQuantity,
                            currentAverageCost = averageCost,
                            purchaseQuantity = incomingQuantity,
                            purchaseUnitCost = incomingUnitCost
                        )
                    }
                    totalQuantity = totalQuantity.add(incomingQuantity)
                }
                else -> {
                    totalQuantity = totalQuantity.add(movement.quantityBaseSigned)
                }
            }
        }

        return InventorySnapshot(
            hasEffectiveHistory = hasHistoryInArea,
            areaQuantityBase = areaQuantity,
            ingredientAverageCostBase = if (hasHistoryInArea || totalQuantity > BigDecimal.ZERO) averageCost else null
        )
    }

    override suspend fun calculateAreaBalancesAt(
        restaurantId: RestaurantId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): Map<IngredientId, BigDecimal> {
        val areaMovements = movementDao.getByArea(areaId.value)
            .map { it.toDomain() }
            .filter { it.effectiveAt <= effectiveAt }

        val reversedMovementIds = areaMovements
            .mapNotNull { it.reversalOfMovementId?.value }
            .toSet()

        val effectiveMovements = areaMovements.filter { movement ->
            movement.movementType != InventoryMovementType.REVERSAL && 
            !reversedMovementIds.contains(movement.id.value)
        }

        val balances = mutableMapOf<IngredientId, BigDecimal>()
        effectiveMovements.forEach { movement ->
            val current = balances.getOrDefault(movement.ingredientId, BigDecimal.ZERO)
            balances[movement.ingredientId] = current.add(movement.quantityBaseSigned)
        }

        return balances
    }
}
