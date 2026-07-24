package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.StockCountAreaEntity
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.database.entity.StockCountLineEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal

data class StockCountValidationGraph(
    val count: StockCountEntity,
    val areas: List<StockCountAreaEntity>,
    val lines: List<StockCountLineEntity>,
    val movements: List<InventoryMovementEntity>
)

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

    fun validateCompletedHistory(graph: StockCountValidationGraph) {
        val count = graph.count
        val lines = graph.lines
        val movements = graph.movements

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
            val area = graph.areas.find { it.id == line.stockCountAreaId } ?: throw ValidationError.StockCountAreaOwnershipMismatch
            
            validateMovementMatchesLine(count, area, line, movement)
        }

        if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
    }

    fun validateVoidedHistory(graph: StockCountValidationGraph) {
        val count = graph.count
        val lines = graph.lines
        val movements = graph.movements

        if (count.completedAt == null || count.voidedAt == null) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        val originals = movements.filter { it.movementType != InventoryMovementType.REVERSAL.name }
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        if (originals.size != lines.size) throw ValidationError.MalformedStockCountMovementHistory
        if (reversals.size != originals.size) throw ValidationError.MalformedStockCountMovementHistory
        if (movements.size != (originals.size + reversals.size)) throw ValidationError.MalformedStockCountMovementHistory

        val originalsByLineId = originals.associateBy { it.sourceLineId }
        val reversalsByOriginalId = reversals.associateBy { it.reversalOfMovementId }

        lines.forEach { line ->
            val original = originalsByLineId[line.id] ?: throw ValidationError.MalformedStockCountMovementHistory
            val area = graph.areas.find { it.id == line.stockCountAreaId } ?: throw ValidationError.StockCountAreaOwnershipMismatch
            validateMovementMatchesLine(count, area, line, original)
            
            val reversal = reversalsByOriginalId[original.id] ?: throw ValidationError.MalformedStockCountMovementHistory
            validateReversalMatchesOriginal(count, original, reversal)
        }
    }

    private fun validateMovementMatchesLine(
        count: StockCountEntity,
        area: StockCountAreaEntity,
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
        if (movement.areaId != area.areaId) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceDocumentType != SourceDocumentType.STOCK_COUNT.name) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceDocumentId != count.id) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceLineId != line.id) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.sourceOperationId != "stock-count-complete:${count.id}:${line.id}") throw ValidationError.MalformedStockCountMovementHistory
        if (movement.reversalOfMovementId != null) throw ValidationError.MalformedStockCountMovementHistory
        
        val adjustmentStr = line.adjustmentQuantityBase ?: throw ValidationError.MalformedStockCountMovementHistory
        val adjustment = try { BigDecimal(adjustmentStr) } catch (e: Exception) { throw ValidationError.MalformedStockCountMovementHistory }
        if (BigDecimal(movement.quantityBaseSigned).compareTo(adjustment) != 0) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        if (movement.effectiveAt != count.effectiveAt) throw ValidationError.MalformedStockCountMovementHistory
        if (movement.createdAt != count.completedAt) throw ValidationError.MalformedStockCountMovementHistory
        
        // Opening balance checks
        if (expectedType == InventoryMovementType.OPENING_BALANCE.name) {
             if (line.expectedQuantityBaseSnapshot != null) throw ValidationError.MalformedStockCountMovementHistory
             if (adjustment.compareTo(BigDecimal(line.quantityBase)) != 0) throw ValidationError.MalformedStockCountMovementHistory
        } else {
             // Adjustment checks
             val expected = try { BigDecimal(line.expectedQuantityBaseSnapshot!!) } catch (e: Exception) { throw ValidationError.MalformedStockCountMovementHistory }
             if (adjustment.compareTo(BigDecimal(line.quantityBase).subtract(expected)) != 0) {
                 throw ValidationError.MalformedStockCountMovementHistory
             }
        }

        validateCostValueConsistency(movement)
    }

    private fun validateCostValueConsistency(movement: InventoryMovementEntity) {
        val costStr = movement.unitCostBaseSnapshot
        val totalStr = movement.totalValueSnapshot
        val qty = BigDecimal(movement.quantityBaseSigned)

        if (costStr == null) {
            if (totalStr != null) throw ValidationError.MalformedStockCountMovementHistory
        } else {
            if (totalStr == null) throw ValidationError.MalformedStockCountMovementHistory
            val cost = try { BigDecimal(costStr) } catch (e: Exception) { throw ValidationError.MalformedStockCountMovementHistory }
            val total = try { BigDecimal(totalStr) } catch (e: Exception) { throw ValidationError.MalformedStockCountMovementHistory }
            if (total.compareTo(qty.multiply(cost, java.math.MathContext.DECIMAL128)) != 0) {
                 throw ValidationError.MalformedStockCountMovementHistory
            }
        }
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
        if (reversal.sourceLineId != original.sourceLineId) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.sourceOperationId != "reversal:${original.id}") throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.reversalOfMovementId != original.id) throw ValidationError.MalformedStockCountMovementHistory
        
        if (BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0) {
            throw ValidationError.MalformedStockCountMovementHistory
        }
        
        val originalCost = original.unitCostBaseSnapshot?.let { BigDecimal(it) }
        val reversalCost = reversal.unitCostBaseSnapshot?.let { BigDecimal(it) }
        if (originalCost != null && reversalCost != null) {
            if (originalCost.compareTo(reversalCost) != 0) throw ValidationError.MalformedStockCountMovementHistory
        } else if (originalCost != reversalCost) {
            throw ValidationError.MalformedStockCountMovementHistory
        }

        val originalTotal = original.totalValueSnapshot?.let { BigDecimal(it) }
        val reversalTotal = reversal.totalValueSnapshot?.let { BigDecimal(it) }
        if (originalTotal != null && reversalTotal != null) {
            if (reversalTotal.compareTo(originalTotal.negate()) != 0) throw ValidationError.MalformedStockCountMovementHistory
        } else if (originalTotal != reversalTotal) {
            throw ValidationError.MalformedStockCountMovementHistory
        }

        if (reversal.effectiveAt != count.voidedAt) throw ValidationError.MalformedStockCountMovementHistory
        if (reversal.createdAt != count.voidedAt) throw ValidationError.MalformedStockCountMovementHistory
    }
}
