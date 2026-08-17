package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class HistoricalInventoryMovement(
    val id: String,
    val movementType: InventoryMovementType,
    val quantityBaseSigned: BigDecimal,
    val unitCostBaseSnapshot: BigDecimal?,
    val sourceDocumentType: SourceDocumentType,
    val sourceDocumentId: String,
    val effectiveAt: Long,
    val createdAt: Long,
    val reversalOfMovementId: String? = null
)

data class HistoricalInventoryCostBoundary(
    val effectiveAtInclusive: Long,
    val createdAtInclusive: Long?
)

data class HistoricalInventoryCostResult(
    val totalQuantityBase: BigDecimal,
    val averageUnitCostBase: BigDecimal?,
    val hasEstablishedCost: Boolean,
    val effectiveMovementIds: Set<String>
)

sealed interface HistoricalInventoryCostCalculationResult {
    data class Success(
        val value: HistoricalInventoryCostResult
    ) : HistoricalInventoryCostCalculationResult

    data class Failure(
        val reason: HistoricalInventoryCostFailure
    ) : HistoricalInventoryCostCalculationResult
}

sealed interface HistoricalInventoryCostFailure {
    data object ReversalTargetMissing : HistoricalInventoryCostFailure
    data object ReversalTargetNotFound : HistoricalInventoryCostFailure
    data object DuplicateReversalTarget : HistoricalInventoryCostFailure
}

data class SourceDocumentIdentity(
    val type: SourceDocumentType,
    val id: String
)

class HistoricalInventoryCostCalculator @Inject constructor() {

    fun calculate(
        movements: List<HistoricalInventoryMovement>,
        boundary: HistoricalInventoryCostBoundary? = null,
        excludedSourceDocument: SourceDocumentIdentity? = null
    ): HistoricalInventoryCostCalculationResult {
        // 1. Filter by boundary and exclusion
        val inBoundary = movements.filter { move ->
            val matchesBoundary = boundary == null ||
                (
                    move.effectiveAt <= boundary.effectiveAtInclusive &&
                    (
                        boundary.createdAtInclusive == null ||
                        move.createdAt <= boundary.createdAtInclusive
                    )
                )

            val matchesExclusion = excludedSourceDocument == null ||
                move.sourceDocumentType != excludedSourceDocument.type ||
                move.sourceDocumentId != excludedSourceDocument.id

            matchesBoundary && matchesExclusion
        }

        // 2. Resolve reversals within the boundary
        val reversalMoves = inBoundary.filter { it.movementType == InventoryMovementType.REVERSAL }
        val inBoundaryIds = inBoundary.map { it.id }.toSet()
        
        // Basic integrity: every reversal must have a target in the set, and no duplicate targets
        val reversalTargetIds = mutableSetOf<String>()
        val effectiveReversedIds = mutableSetOf<String>()
        val effectiveReversalIds = mutableSetOf<String>()

        for (rev in reversalMoves) {
            val targetId = rev.reversalOfMovementId
            if (targetId == null) {
                return HistoricalInventoryCostCalculationResult.Failure(HistoricalInventoryCostFailure.ReversalTargetMissing)
            }
            if (!inBoundaryIds.contains(targetId)) {
                return HistoricalInventoryCostCalculationResult.Failure(HistoricalInventoryCostFailure.ReversalTargetNotFound)
            }
            if (!reversalTargetIds.add(targetId)) {
                return HistoricalInventoryCostCalculationResult.Failure(HistoricalInventoryCostFailure.DuplicateReversalTarget)
            }
            effectiveReversedIds.add(targetId)
            effectiveReversalIds.add(rev.id)
        }

        // 3. Filter effective movements and sort
        val sortedMovements = inBoundary
            .filter { it.id !in effectiveReversedIds && it.id !in effectiveReversalIds }
            .sortedWith(compareBy({ it.effectiveAt }, { it.createdAt }, { it.id }))

        var currentTotalQuantity = BigDecimal.ZERO
        var currentAverageCost = BigDecimal.ZERO
        var hasEstablishedCost = false

        for (move in sortedMovements) {
            when (move.movementType) {
                InventoryMovementType.PURCHASE,
                InventoryMovementType.OPENING_BALANCE,
                InventoryMovementType.PRODUCTION_OUTPUT -> {
                    val incomingQuantity = move.quantityBaseSigned
                    val incomingUnitCost = move.unitCostBaseSnapshot

                    if (incomingQuantity > BigDecimal.ZERO && incomingUnitCost != null) {
                        if (!hasEstablishedCost || currentTotalQuantity <= BigDecimal.ZERO) {
                            currentAverageCost = incomingUnitCost
                        } else {
                            val currentTotalValue = currentTotalQuantity.multiply(currentAverageCost, MathContext.DECIMAL128)
                            val incomingTotalValue = incomingQuantity.multiply(incomingUnitCost, MathContext.DECIMAL128)
                            val newTotalQuantity = currentTotalQuantity.add(incomingQuantity)
                            
                            currentAverageCost = currentTotalValue.add(incomingTotalValue)
                                .divide(newTotalQuantity, MathContext.DECIMAL128)
                        }
                        hasEstablishedCost = true
                    }
                    currentTotalQuantity = currentTotalQuantity.add(incomingQuantity)
                }
                InventoryMovementType.WASTE,
                InventoryMovementType.COUNT_ADJUSTMENT,
                InventoryMovementType.MANUAL_ADJUSTMENT,
                InventoryMovementType.PRODUCTION_CONSUMPTION,
                InventoryMovementType.SALES_CONSUMPTION -> {
                    currentTotalQuantity = currentTotalQuantity.add(move.quantityBaseSigned)
                }
                InventoryMovementType.REVERSAL -> {
                    // Reversals already handled by filtering
                }
                InventoryMovementType.UNKNOWN -> {
                    // Unknown movements are excluded from quantity and cost calculations
                }
            }
        }

        return HistoricalInventoryCostCalculationResult.Success(
            HistoricalInventoryCostResult(
                totalQuantityBase = currentTotalQuantity,
                averageUnitCostBase = if (hasEstablishedCost) currentAverageCost else null,
                hasEstablishedCost = hasEstablishedCost,
                effectiveMovementIds = sortedMovements
                    .filter { it.movementType != InventoryMovementType.UNKNOWN }
                    .map { it.id }
                    .toSet()
            )
        )
    }
}
