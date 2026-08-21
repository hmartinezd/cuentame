package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.database.entity.WasteEventEntity
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryMovementOperationIds
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import javax.inject.Inject

class WasteMovementHistoryValidator @Inject constructor(
    private val movementValidator: InventoryMovementValidator
) {
    private fun parseHistoryDecimal(value: String): BigDecimal {
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    fun validateDraftHistory(movements: List<InventoryMovementEntity>) {
        if (movements.isNotEmpty()) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    fun validatePostedHistory(
        event: WasteEventEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (event.status != DocumentStatus.POSTED.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.postedAt == null || event.voidedAt != null) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.updatedAt < event.postedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.createdAt > event.updatedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movements.size != 1) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        val movement = movements.first()
        validateOriginalMovementMatchesEvent(event, movement)
    }

    fun validateVoidedHistory(
        event: WasteEventEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (event.status != DocumentStatus.VOIDED.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.postedAt == null || event.voidedAt == null || event.voidedAt < event.postedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.updatedAt < event.voidedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movements.size != 2) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        val original = movements.find { it.movementType == InventoryMovementType.WASTE.name }
            ?: throw ValidationError.MalformedWasteMovementHistory
        val reversal = movements.find { it.movementType == InventoryMovementType.REVERSAL.name }
            ?: throw ValidationError.MalformedWasteMovementHistory

        validateOriginalMovementMatchesEvent(event, original)
        
        try {
            movementValidator.validateReversal(original, reversal)
        } catch (e: Exception) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (reversal.reversalOfMovementId != original.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.sourceDocumentType != SourceDocumentType.WASTE_EVENT.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.sourceDocumentId != event.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.sourceLineId != event.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.sourceOperationId != InventoryMovementOperationIds.reversal(original.id)) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.effectiveAt != event.voidedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.createdAt != event.voidedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        val revQty = parseHistoryDecimal(reversal.quantityBaseSigned)
        val origQty = parseHistoryDecimal(original.quantityBaseSigned)
        if (revQty.compareTo(origQty.negate()) != 0 || revQty <= BigDecimal.ZERO) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    private fun validateOriginalMovementMatchesEvent(
        event: WasteEventEntity,
        movement: InventoryMovementEntity
    ) {
        try {
            movementValidator.validateMovement(movement)
        } catch (e: Exception) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movement.movementType != InventoryMovementType.WASTE.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.reversalOfMovementId != null) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.restaurantId != event.restaurantId) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.ingredientId != event.ingredientId) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.areaId != event.areaId) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        
        val eventQtyBase = parseHistoryDecimal(event.quantityBase)
        val movementQtyBase = parseHistoryDecimal(movement.quantityBaseSigned)
        
        if (eventQtyBase <= BigDecimal.ZERO) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movementQtyBase.compareTo(eventQtyBase.negate()) != 0 || movementQtyBase >= BigDecimal.ZERO) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movement.sourceDocumentType != SourceDocumentType.WASTE_EVENT.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.sourceDocumentId != event.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.sourceLineId != event.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.sourceOperationId != InventoryMovementOperationIds.wastePost(event.id)) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movement.effectiveAt != event.effectiveAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.createdAt != event.postedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        val cost = movement.unitCostBaseSnapshot?.let { parseHistoryDecimal(it) }
        val total = movement.totalValueSnapshot?.let { parseHistoryDecimal(it) }

        if (cost == null && total != null) throw ValidationError.MalformedWasteMovementHistory
        if (cost != null && total == null) throw ValidationError.MalformedWasteMovementHistory
        if (cost != null && total != null) {
            val expectedTotal = movementQtyBase.multiply(cost, java.math.MathContext.DECIMAL128)
            if (total.compareTo(expectedTotal) != 0 || total > BigDecimal.ZERO) {
                throw ValidationError.MalformedWasteMovementHistory
            }
        }
    }
}
