package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.database.entity.StockCountLineEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal

class StockCountMovementHistoryValidator {

    fun validateDraftHistory(
        count: StockCountEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (count.completedAt != null || count.voidedAt != null) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        if (movements.isNotEmpty()) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
    }

    fun validateCompletedHistory(
        count: StockCountEntity,
        lines: List<StockCountLineEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (count.completedAt == null || count.voidedAt != null) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        if (lines.isEmpty()) throw ValidationError.StockCountHasNoLines
        
        val movementsByLineId = movements.groupBy { it.sourceLineId }
        
        if (movementsByLineId.size != lines.size || movements.size != lines.size) {
            throw ValidationError.MalformedStockCountMovementHistory
        }

        lines.forEach { line ->
            val lineMovements = movementsByLineId[line.id] ?: throw ValidationError.MalformedStockCountMovementHistory
            if (lineMovements.size != 1) throw ValidationError.MalformedStockCountMovementHistory
            
            val movement = lineMovements.first()
            validateMovementMatchesLine(count, line, movement)
        }

        if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
    }

    fun validateVoidedHistory(
        count: StockCountEntity,
        lines: List<StockCountLineEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (count.completedAt == null || count.voidedAt == null) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        val purchases = movements.filter { it.movementType != InventoryMovementType.REVERSAL.name }
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        if (purchases.size != lines.size) throw ValidationError.MalformedStockCountMovementHistory
        if (reversals.size != purchases.size) throw ValidationError.MalformedStockCountMovementHistory
        if (movements.size != (purchases.size + reversals.size)) throw ValidationError.MalformedStockCountMovementHistory

        val purchasesByLineId = purchases.associateBy { it.sourceLineId }
        val reversalsByOriginalId = reversals.associateBy { it.reversalOfMovementId }

        lines.forEach { line ->
            val purchase = purchasesByLineId[line.id] ?: throw ValidationError.MalformedStockCountMovementHistory
            validateMovementMatchesLine(count, line, purchase)
            
            val reversal = reversalsByOriginalId[purchase.id] ?: throw ValidationError.MalformedStockCountMovementHistory
            validateReversalMatchesOriginal(count, purchase, reversal)
        }
    }

    private fun validateMovementMatchesLine(
        count: StockCountEntity,
        line: StockCountLineEntity,
        movement: InventoryMovementEntity
    ) {
        val expectedType = if (line.expectedQuantityBaseSnapshot == null) {
            InventoryMovementType.OPENING_BALANCE.name
        } else {
            InventoryMovementType.COUNT_ADJUSTMENT.name
        }

        if (movement.movementType != expectedType) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.restaurantId != count.restaurantId) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.ingredientId != line.ingredientId) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceDocumentType != SourceDocumentType.STOCK_COUNT.name) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceDocumentId != count.id) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceOperationId != "stock-count-complete:${count.id}:${line.id}") throw ValidationError.MalformedStockCountMovementHistory
        if (movement.reversalOfMovementId != null) throw ValidationError.MalformedStockCountMovementHistory
        
        val adjustment = line.adjustmentQuantityBase ?: "0"
        if (BigDecimal(movement.quantityBaseSigned).compareTo(BigDecimal(adjustment)) != 0) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        if (movement.effectiveAt != count.effectiveAt) throw ValidationError.MalformedStockCountMovementHistory
    }

    private fun validateReversalMatchesOriginal(
        count: StockCountEntity,
        original: InventoryMovementEntity,
        reversal: InventoryMovementEntity
    ) {
        if (reversal.movementType != InventoryMovementType.REVERSAL.name) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.restaurantId != original.restaurantId) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.ingredientId != original.ingredientId) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.areaId != original.areaId) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.sourceDocumentType != SourceDocumentType.STOCK_COUNT.name) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.sourceDocumentId != count.id) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.sourceOperationId != "reversal:${original.id}") throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.reversalOfMovementId != original.id) throw ValidationError.MalformedStockCountMovementHistory
        
        if (BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        if (reversal.effectiveAt != count.voidedAt) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.createdAt != count.voidedAt) throw ValidationError.MalformedStockCountMovementHistory
    }
}
