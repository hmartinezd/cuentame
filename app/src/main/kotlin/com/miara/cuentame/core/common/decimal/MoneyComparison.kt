package com.miara.cuentame.core.common.decimal

import java.math.BigDecimal

object MoneyComparison {
    private val MONEY_TOLERANCE = BigDecimal("0.02")

    /**
     * Checks if two monetary values are approximately equal within a tolerance of 0.02.
     * The comparison is inclusive of the boundary.
     */
    fun moneyApproximatelyEquals(
        expected: BigDecimal,
        actual: BigDecimal
    ): Boolean {
        return expected
            .subtract(actual)
            .abs()
            .compareTo(MONEY_TOLERANCE) <= 0
    }
}
