package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import java.math.BigDecimal

class InventoryMovementService(
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) {
    fun createReversal(original: InventoryMovement): InventoryMovement {
        if (original.movementType == InventoryMovementType.REVERSAL) {
            throw IllegalArgumentException("Cannot reverse a reversal movement")
        }
        
        return original.copy(
            id = InventoryMovementId(idGenerator.newId()),
            movementType = InventoryMovementType.REVERSAL,
            quantityBaseSigned = original.quantityBaseSigned.negate(),
            reversalOfMovementId = original.id,
            createdAt = timeProvider.now()
        )
    }
}
