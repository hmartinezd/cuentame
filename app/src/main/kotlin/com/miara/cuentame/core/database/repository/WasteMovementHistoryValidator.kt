package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
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

        if (movements.size != 1) {
            throw ValidationError.MalformedWasteMovementHistory
        }

        val movement = movements.first()
        validateOriginalMovementMatchesEvent(event, movement)
        
        if (movement.reversalOfMovementId != null) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    fun validateVoidedHistory(
        event: WasteEventEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (event.status != DocumentStatus.VOIDED.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (event.postedAt == null || event.voidedAt == null) {
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
        movementValidator.validateReversal(original, reversal)

        if (reversal.sourceDocumentType != SourceDocumentType.WASTE_EVENT.name) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.sourceDocumentId != event.id) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.effectiveAt != event.voidedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (reversal.createdAt != event.voidedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    private fun validateOriginalMovementMatchesEvent(
        event: WasteEventEntity,
        movement: InventoryMovementEntity
    ) {
        movementValidator.validateMovement(movement)

        if (movement.movementType != InventoryMovementType.WASTE.name) {
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
        
        if (movementQtyBase.compareTo(eventQtyBase.negate()) != 0) {
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
        if (movement.sourceOperationId != "waste-post:${event.id}") {
            throw ValidationError.MalformedWasteMovementHistory
        }

        if (movement.effectiveAt != event.effectiveAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
        if (movement.createdAt != event.postedAt) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }
}
