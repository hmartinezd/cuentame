package com.miara.cuentame.core.domain.service

import java.math.BigDecimal

class CountComparisonCalculator {
    fun calculateUnclassifiedUsage(
        initialInventory: BigDecimal,
        purchases: BigDecimal,
        recordedWaste: BigDecimal,
        finalInventory: BigDecimal
    ): BigDecimal {
        // unclassified usage = initial + purchases - waste - final
        return initialInventory
            .add(purchases)
            .subtract(recordedWaste)
            .subtract(finalInventory)
    }
}
