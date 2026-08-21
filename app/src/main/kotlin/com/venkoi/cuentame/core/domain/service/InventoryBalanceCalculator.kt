package com.venkoi.cuentame.core.domain.service

import com.venkoi.cuentame.core.model.inventory.InventoryMovement
import java.math.BigDecimal

class InventoryBalanceCalculator {
    fun calculateBalance(
        movements: List<InventoryMovement>
    ): BigDecimal {
        return movements.fold(BigDecimal.ZERO) { acc, movement ->
            acc.add(movement.quantityBaseSigned)
        }
    }
}
