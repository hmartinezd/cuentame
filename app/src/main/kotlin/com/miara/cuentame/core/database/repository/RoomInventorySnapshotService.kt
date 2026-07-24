package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomInventorySnapshotService @Inject constructor(
    private val movementDao: InventoryMovementDao,
    private val costCalculator: WeightedAverageCostCalculator,
    private val validator: InventoryMovementValidator
) : InventorySnapshotService {

    override suspend fun calculateAt(
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): InventorySnapshot {
        val movements = movementDao.getByRestaurantAndIngredientUpTo(
            restaurantId.value,
            ingredientId.value,
            effectiveAt.toEpochMilli()
        )

        val reversedIds = mutableSetOf<String>()
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
        
        reversals.forEach { reversal ->
            val originalId = reversal.reversalOfMovementId ?: return@forEach
            val original = movements.find { it.id == originalId } ?: throw com.miara.cuentame.core.domain.validation.ValidationError.MalformedInventoryMovementHistory
            validator.validateReversal(original, reversal)
            if (reversedIds.contains(originalId)) throw com.miara.cuentame.core.domain.validation.ValidationError.MalformedInventoryMovementHistory
            reversedIds.add(originalId)
        }

        val effectiveMovements = movements.filter { 
            it.movementType != InventoryMovementType.REVERSAL.name && !reversedIds.contains(it.id) 
        }

        var areaQuantity = BigDecimal.ZERO
        var totalQuantity = BigDecimal.ZERO
        var averageCost = BigDecimal.ZERO
        var hasEffectiveHistoryInArea = false
        var hasEstablishedCost = false

        effectiveMovements.forEach { movementEntity ->
            val movement = movementEntity.toDomain()
            val isTargetArea = movement.areaId == areaId
            
            if (isTargetArea) {
                areaQuantity = areaQuantity.add(movement.quantityBaseSigned)
                hasEffectiveHistoryInArea = true
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
                        hasEstablishedCost = true
                    }
                    totalQuantity = totalQuantity.add(incomingQuantity)
                }
                else -> {
                    totalQuantity = totalQuantity.add(movement.quantityBaseSigned)
                }
            }
        }

        return InventorySnapshot(
            hasEffectiveHistory = hasEffectiveHistoryInArea,
            areaQuantityBase = areaQuantity,
            ingredientAverageCostBase = if (hasEstablishedCost) averageCost else null
        )
    }

    override suspend fun calculateAreaBalancesAt(
        restaurantId: RestaurantId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): Map<IngredientId, BigDecimal> {
        val movements = movementDao.getByRestaurantAndAreaUpTo(
            restaurantId.value,
            areaId.value,
            effectiveAt.toEpochMilli()
        )

        val reversedIds = mutableSetOf<String>()
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        reversals.forEach { reversal ->
            val originalId = reversal.reversalOfMovementId ?: return@forEach
            val original = movements.find { it.id == originalId } ?: throw com.miara.cuentame.core.domain.validation.ValidationError.MalformedInventoryMovementHistory
            validator.validateReversal(original, reversal)
            if (reversedIds.contains(originalId)) throw com.miara.cuentame.core.domain.validation.ValidationError.MalformedInventoryMovementHistory
            reversedIds.add(originalId)
        }

        val effectiveMovements = movements.filter {
            it.movementType != InventoryMovementType.REVERSAL.name && !reversedIds.contains(it.id)
        }

        val balances = mutableMapOf<IngredientId, BigDecimal>()
        effectiveMovements.forEach { movement ->
            val ingredientId = IngredientId(movement.ingredientId)
            val current = balances.getOrDefault(ingredientId, BigDecimal.ZERO)
            balances[ingredientId] = current.add(BigDecimal(movement.quantityBaseSigned))
        }

        return balances
    }
}
