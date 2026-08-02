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
    val createdAt: Long
)

data class HistoricalInventoryCostResult(
    val totalQuantityBase: BigDecimal,
    val averageUnitCostBase: BigDecimal?,
    val hasEstablishedCost: Boolean
)

data class SourceDocumentIdentity(
    val type: SourceDocumentType,
    val id: String
)

class HistoricalInventoryCostCalculator @Inject constructor() {

    fun calculate(
        movements: List<HistoricalInventoryMovement>,
        excludedSourceDocument: SourceDocumentIdentity? = null
    ): HistoricalInventoryCostResult {
        // Filter out excluded documents and sort deterministically
        val sortedMovements = movements
            .filter { move ->
                excludedSourceDocument == null || 
                move.sourceDocumentType != excludedSourceDocument.type || 
                move.sourceDocumentId != excludedSourceDocument.id
            }
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
                InventoryMovementType.PRODUCTION_CONSUMPTION -> {
                    currentTotalQuantity = currentTotalQuantity.add(move.quantityBaseSigned)
                }
                InventoryMovementType.REVERSAL -> {
                    // Reversals should ideally be handled by the caller filtering them out of effective history.
                    // If they reach here, we ignore them to avoid double-counting or complex reversal lookup.
                }
            }
        }

        return HistoricalInventoryCostResult(
            totalQuantityBase = currentTotalQuantity,
            averageUnitCostBase = if (hasEstablishedCost) currentAverageCost else null,
            hasEstablishedCost = hasEstablishedCost
        )
    }
}
