package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.domain.service.HistoricalInventoryMovement
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.domain.validation.ValidationError
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomInventorySnapshotService @Inject constructor(
    private val movementDao: InventoryMovementDao,
    private val costCalculator: HistoricalInventoryCostCalculator,
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
            val originalId = reversal.reversalOfMovementId ?: throw ValidationError.MalformedInventoryMovementHistory
            val original = movements.find { it.id == originalId } ?: throw ValidationError.MalformedInventoryMovementHistory
            validator.validateReversal(original, reversal)
            if (reversedIds.contains(originalId)) throw ValidationError.MalformedInventoryMovementHistory
            reversedIds.add(originalId)
        }

        val effectiveMovements = movements.filter { 
            it.movementType != InventoryMovementType.REVERSAL.name && !reversedIds.contains(it.id) 
        }

        var areaQuantity = BigDecimal.ZERO
        var hasEffectiveHistoryInArea = false
        
        val historicalMoves = effectiveMovements.map { move ->
            if (move.areaId == areaId.value) {
                areaQuantity = areaQuantity.add(BigDecimal(move.quantityBaseSigned))
                hasEffectiveHistoryInArea = true
            }
            validator.validateMovement(move)
            HistoricalInventoryMovement(
                id = move.id,
                movementType = InventoryMovementType.valueOf(move.movementType),
                quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                sourceDocumentType = SourceDocumentType.valueOf(move.sourceDocumentType),
                sourceDocumentId = move.sourceDocumentId,
                effectiveAt = move.effectiveAt,
                createdAt = move.createdAt
            )
        }

        val costResult = costCalculator.calculate(historicalMoves)

        return InventorySnapshot(
            hasEffectiveHistory = hasEffectiveHistoryInArea,
            areaQuantityBase = areaQuantity,
            ingredientAverageCostBase = costResult.averageUnitCostBase
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
            val originalId = reversal.reversalOfMovementId ?: throw ValidationError.MalformedInventoryMovementHistory
            val original = movements.find { it.id == originalId } ?: throw ValidationError.MalformedInventoryMovementHistory
            validator.validateReversal(original, reversal)
            if (reversedIds.contains(originalId)) throw ValidationError.MalformedInventoryMovementHistory
            reversedIds.add(originalId)
        }

        val effectiveMovements = movements.filter {
            it.movementType != InventoryMovementType.REVERSAL.name && !reversedIds.contains(it.id)
        }

        val balances = mutableMapOf<IngredientId, BigDecimal>()
        effectiveMovements.forEach { movementEntity ->
            validator.validateMovement(movementEntity)
            val movement = movementEntity.toDomain()
            val current = balances.getOrDefault(movement.ingredientId, BigDecimal.ZERO)
            balances[movement.ingredientId] = current.add(movement.quantityBaseSigned)
        }

        return balances
    }
}
