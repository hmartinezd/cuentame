package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal

class PurchaseMovementHistoryValidator {

    fun validateDraftHistory(
        receipt: PurchaseReceiptEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (receipt.postedAt != null || receipt.voidedAt != null) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (movements.isNotEmpty()) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
    }

    fun validatePostedHistory(
        receipt: PurchaseReceiptEntity,
        lines: List<PurchaseLineEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (receipt.postedAt == null || receipt.voidedAt != null) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (lines.isEmpty()) throw ValidationError.PurchaseHasNoLines
        
        // Exact one-to-one mapping
        val movementsByLineId = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
            .groupBy { it.sourceLineId }
        
        if (movementsByLineId.size != lines.size || movements.size != lines.size) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }

        lines.forEach { line ->
            val lineMovements = movementsByLineId[line.id] ?: throw ValidationError.MalformedPurchaseMovementHistory
            if (lineMovements.size != 1) throw ValidationError.MalformedPurchaseMovementHistory
            
            val movement = lineMovements.first()
            validateMovementMatchesLine(receipt, line, movement)
        }

        // Ensure no reversals exist in POSTED state
        if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
    }

    fun validateVoidedHistory(
        receipt: PurchaseReceiptEntity,
        lines: List<PurchaseLineEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (receipt.postedAt == null || receipt.voidedAt == null) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        val purchases = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        if (purchases.size != lines.size) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversals.size != purchases.size) throw ValidationError.MalformedPurchaseMovementHistory
        if (movements.size != (purchases.size + reversals.size)) throw ValidationError.MalformedPurchaseMovementHistory

        val purchasesByLineId = purchases.associateBy { it.sourceLineId }
        val reversalsByOriginalId = reversals.associateBy { it.reversalOfMovementId }

        lines.forEach { line ->
            val purchase = purchasesByLineId[line.id] ?: throw ValidationError.MalformedPurchaseMovementHistory
            validateMovementMatchesLine(receipt, line, purchase)
            
            val reversal = reversalsByOriginalId[purchase.id] ?: throw ValidationError.MalformedPurchaseMovementHistory
            validateReversalMatchesOriginal(receipt, purchase, reversal)
        }
    }

    private fun validateMovementMatchesLine(
        receipt: PurchaseReceiptEntity,
        line: PurchaseLineEntity,
        movement: InventoryMovementEntity
    ) {
        if (movement.movementType != InventoryMovementType.PURCHASE.name) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.restaurantId != receipt.restaurantId) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.ingredientId != line.ingredientId) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.areaId != line.areaId) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.sourceDocumentType != SourceDocumentType.PURCHASE_RECEIPT.name) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.sourceDocumentId != receipt.id) throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.sourceOperationId != "purchase-post:${receipt.id}:${line.id}") throw ValidationError.MalformedPurchaseMovementHistory
        if (movement.reversalOfMovementId != null) throw ValidationError.MalformedPurchaseMovementHistory
        
        val qty = BigDecimal(movement.quantityBaseSigned)
        if (qty <= BigDecimal.ZERO || qty.compareTo(BigDecimal(line.quantityBase)) != 0) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (movement.unitCostBaseSnapshot == null || BigDecimal(movement.unitCostBaseSnapshot).compareTo(BigDecimal(line.unitCostBase)) != 0) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (movement.totalValueSnapshot == null || BigDecimal(movement.totalValueSnapshot).compareTo(BigDecimal(line.lineTotal)) != 0) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (movement.effectiveAt != receipt.purchaseDate) throw ValidationError.MalformedPurchaseMovementHistory
    }

    private fun validateReversalMatchesOriginal(
        receipt: PurchaseReceiptEntity,
        original: InventoryMovementEntity,
        reversal: InventoryMovementEntity
    ) {
        if (reversal.movementType != InventoryMovementType.REVERSAL.name) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.restaurantId != original.restaurantId) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.ingredientId != original.ingredientId) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.areaId != original.areaId) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.sourceDocumentType != SourceDocumentType.PURCHASE_RECEIPT.name) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.sourceDocumentId != receipt.id) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.sourceOperationId != "reversal:${original.id}") throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.sourceLineId != original.sourceLineId) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.reversalOfMovementId != original.id) throw ValidationError.MalformedPurchaseMovementHistory
        
        if (BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0) {
            throw ValidationError.MalformedPurchaseMovementHistory
        }
        if (reversal.unitCostBaseSnapshot != original.unitCostBaseSnapshot) {
             throw ValidationError.MalformedPurchaseMovementHistory
        }
        
        val originalTotal = original.totalValueSnapshot?.let { BigDecimal(it) }
        val reversalTotal = reversal.totalValueSnapshot?.let { BigDecimal(it) }
        
        if (originalTotal == null && reversalTotal != null) throw ValidationError.MalformedPurchaseMovementHistory
        if (originalTotal != null && reversalTotal == null) throw ValidationError.MalformedPurchaseMovementHistory
        if (originalTotal != null && reversalTotal != null) {
            if (reversalTotal.compareTo(originalTotal.negate()) != 0) {
                throw ValidationError.MalformedPurchaseMovementHistory
            }
        }
        
        if (reversal.effectiveAt != receipt.voidedAt) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversal.createdAt != receipt.voidedAt) throw ValidationError.MalformedPurchaseMovementHistory
    }
}
