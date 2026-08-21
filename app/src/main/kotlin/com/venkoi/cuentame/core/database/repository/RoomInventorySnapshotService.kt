package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.dao.InventoryMovementDao
import com.venkoi.cuentame.core.database.mapper.toDomain
import com.venkoi.cuentame.core.domain.service.HistoricalInventoryCostCalculationResult
import com.venkoi.cuentame.core.domain.service.HistoricalInventoryCostFailure
import com.venkoi.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.venkoi.cuentame.core.domain.service.HistoricalInventoryMovement
import com.venkoi.cuentame.core.domain.service.InventorySnapshot
import com.venkoi.cuentame.core.domain.service.InventorySnapshotService
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import com.venkoi.cuentame.core.domain.validation.ValidationError
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class RoomInventorySnapshotService @Inject constructor(
    private val movementDao: InventoryMovementDao,
    private val costCalculator: HistoricalInventoryCostCalculator,
    private val historyValidator: InventoryMovementHistoryValidator
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

        historyValidator.validateCompleteHistory(movements)

        val historicalMoves = movements.map { move ->
            HistoricalInventoryMovement(
                id = move.id,
                movementType = parsePersistedEnum(move.movementType, InventoryMovementType.UNKNOWN),
                quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                sourceDocumentType = parsePersistedEnum(move.sourceDocumentType, SourceDocumentType.UNKNOWN),
                sourceDocumentId = move.sourceDocumentId,
                effectiveAt = move.effectiveAt,
                createdAt = move.createdAt,
                reversalOfMovementId = move.reversalOfMovementId
            )
        }

        val calculationResult = costCalculator.calculate(historicalMoves)

        val costResult = when (calculationResult) {
            is HistoricalInventoryCostCalculationResult.Success -> calculationResult.value
            is HistoricalInventoryCostCalculationResult.Failure -> {
                throw ValidationError.MalformedInventoryMovementHistory
            }
        }

        val effectiveMovements = movements.filter { it.id in costResult.effectiveMovementIds }

        var areaQuantity = BigDecimal.ZERO
        var hasEffectiveHistoryInArea = false
        
        effectiveMovements.forEach { move ->
            if (move.areaId == areaId.value) {
                areaQuantity = areaQuantity.add(BigDecimal(move.quantityBaseSigned))
                hasEffectiveHistoryInArea = true
            }
        }

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

        // Group by ingredient to correctly calculate effective history for each
        val movementsByIngredient = movements.groupBy { it.ingredientId }
        val balances = mutableMapOf<IngredientId, BigDecimal>()

        movementsByIngredient.forEach { (ingredientId, ingredientMovements) ->
            historyValidator.validateCompleteHistory(ingredientMovements)

            val historicalMoves = ingredientMovements.map { move ->
                HistoricalInventoryMovement(
                    id = move.id,
                    movementType = parsePersistedEnum(move.movementType, InventoryMovementType.UNKNOWN),
                    quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                    unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                    sourceDocumentType = parsePersistedEnum(move.sourceDocumentType, SourceDocumentType.UNKNOWN),
                    sourceDocumentId = move.sourceDocumentId,
                    effectiveAt = move.effectiveAt,
                    createdAt = move.createdAt,
                    reversalOfMovementId = move.reversalOfMovementId
                )
            }

            val calculationResult = costCalculator.calculate(historicalMoves)

            val costResult = when (calculationResult) {
                is HistoricalInventoryCostCalculationResult.Success -> calculationResult.value
                is HistoricalInventoryCostCalculationResult.Failure -> {
                    throw ValidationError.MalformedInventoryMovementHistory
                }
            }

            val effectiveMovements = ingredientMovements.filter { it.id in costResult.effectiveMovementIds }
            
            var quantity = BigDecimal.ZERO
            effectiveMovements.forEach { move ->
                 quantity = quantity.add(BigDecimal(move.quantityBaseSigned))
            }
            if (quantity.compareTo(BigDecimal.ZERO) != 0 || effectiveMovements.isNotEmpty()) {
                balances[IngredientId(ingredientId)] = quantity
            }
        }

        return balances
    }
}
