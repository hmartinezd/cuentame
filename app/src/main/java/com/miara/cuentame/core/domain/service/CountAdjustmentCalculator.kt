package com.miara.cuentame.core.domain.service

import java.math.BigDecimal

class CountAdjustmentCalculator {
    fun calculateAdjustment(
        countedQuantity: BigDecimal,
        expectedQuantity: BigDecimal
    ): BigDecimal {
        return countedQuantity.subtract(expectedQuantity)
    }
}
