package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal

class InventoryMovementValidator {
    
    fun validateMovement(movement: InventoryMovementEntity) {
        // Identity validation
        if (movement.id.isBlank() ||
            movement.restaurantId.isBlank() ||
            movement.ingredientId.isBlank() ||
            movement.areaId.isBlank() ||
            movement.sourceDocumentId.isBlank() ||
            movement.sourceOperationId.isBlank()
        ) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        // Validate decimals
        try {
            BigDecimal(movement.quantityBaseSigned)
            movement.unitCostBaseSnapshot?.let { BigDecimal(it) }
            movement.totalValueSnapshot?.let { BigDecimal(it) }
        } catch (e: Exception) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        // Validate movement type (Resilient parsing)
        if (movement.movementType.isBlank()) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        // Validate source document type (Resilient parsing)
        if (movement.sourceDocumentType.isBlank()) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        if (movement.movementType == InventoryMovementType.REVERSAL.name) {
            if (movement.reversalOfMovementId == null) {
                throw ValidationError.MalformedInventoryMovementHistory
            }
        } else {
            if (movement.reversalOfMovementId != null) {
                throw ValidationError.MalformedInventoryMovementHistory
            }
        }
    }

    fun validateReversal(original: InventoryMovementEntity, reversal: InventoryMovementEntity) {
        validateMovement(original)
        validateMovement(reversal)

        if (reversal.movementType != InventoryMovementType.REVERSAL.name) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.reversalOfMovementId != original.id) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (original.movementType == InventoryMovementType.REVERSAL.name) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        
        if (reversal.restaurantId != original.restaurantId) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.ingredientId != original.ingredientId) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.areaId != original.areaId) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.sourceDocumentType != original.sourceDocumentType) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.sourceDocumentId != original.sourceDocumentId) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.sourceLineId != original.sourceLineId) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.sourceOperationId != InventoryMovementOperationIds.reversal(original.id)) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        if (reversal.effectiveAt < original.effectiveAt) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.createdAt < original.createdAt) {
            throw ValidationError.MalformedInventoryMovementHistory
        }

        if (BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        
        val originalCost = original.unitCostBaseSnapshot?.let { BigDecimal(it) }
        val reversalCost = reversal.unitCostBaseSnapshot?.let { BigDecimal(it) }

        if (originalCost != null && reversalCost != null) {
             if (originalCost.compareTo(reversalCost) != 0) throw ValidationError.MalformedInventoryMovementHistory
        } else if (originalCost != reversalCost) {
             throw ValidationError.MalformedInventoryMovementHistory
        }

        val originalTotal = original.totalValueSnapshot?.let { BigDecimal(it) }
        val reversalTotal = reversal.totalValueSnapshot?.let { BigDecimal(it) }
        
        if (originalTotal == null && reversalTotal == null) {
            // OK
        } else if (originalTotal != null && reversalTotal != null) {
            if (reversalTotal.compareTo(originalTotal.negate()) != 0) throw ValidationError.MalformedInventoryMovementHistory
        } else {
            throw ValidationError.MalformedInventoryMovementHistory
        }
    }
}
