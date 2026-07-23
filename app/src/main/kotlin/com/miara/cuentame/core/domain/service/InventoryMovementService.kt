package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType

class InventoryMovementService(
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) {
    fun createReversal(original: InventoryMovement): InventoryMovement {
        if (original.movementType == InventoryMovementType.REVERSAL) {
            throw ValidationError.CannotReverseReversal
        }
        
        return original.copy(
            id = InventoryMovementId(idGenerator.newId()),
            movementType = InventoryMovementType.REVERSAL,
            quantityBaseSigned = original.quantityBaseSigned.negate(),
            unitCostBaseSnapshot = original.unitCostBaseSnapshot,
            totalValueSnapshot = original.totalValueSnapshot?.negate(),
            reversalOfMovementId = original.id,
            sourceOperationId = "reversal:${original.id.value}",
            createdAt = timeProvider.now()
        )
    }
}
