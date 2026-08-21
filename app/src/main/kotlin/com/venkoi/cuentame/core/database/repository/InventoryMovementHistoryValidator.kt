package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.domain.validation.ValidationError
import javax.inject.Inject

class InventoryMovementHistoryValidator @Inject constructor(
    private val validator: InventoryMovementValidator
) {

    fun validateCompleteHistory(movements: List<InventoryMovementEntity>) {
        val movementMap = movements.associateBy { it.id }
        val reversals = movements.filter { it.reversalOfMovementId != null }
        val reversedIds = mutableSetOf<String>()

        movements.forEach { move ->
            validator.validateMovement(move)
        }

        reversals.forEach { reversal ->
            val targetId = reversal.reversalOfMovementId!!
            
            val original = movementMap[targetId]
                ?: throw ValidationError.MalformedInventoryMovementHistory
            
            if (original.reversalOfMovementId != null) {
                throw ValidationError.MalformedInventoryMovementHistory
            }
            
            if (reversal.id == targetId) {
                throw ValidationError.MalformedInventoryMovementHistory
            }

            if (reversedIds.contains(targetId)) {
                throw ValidationError.MalformedInventoryMovementHistory
            }
            reversedIds.add(targetId)

            validator.validateReversal(original, reversal)
        }
    }
}
