package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal

class InventoryMovementValidator {
    fun validateReversal(original: InventoryMovementEntity, reversal: InventoryMovementEntity) {
        if (reversal.movementType != InventoryMovementType.REVERSAL.name) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (reversal.reversalOfMovementId == null) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (original.movementType == InventoryMovementType.REVERSAL.name) {
            throw ValidationError.MalformedInventoryMovementHistory
        }
        if (original.reversalOfMovementId != null) {
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
