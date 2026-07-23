package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.domain.validation.ValidationError
import java.math.BigDecimal
import java.math.MathContext

data class CalculatedPurchaseLine(
    val quantityBase: BigDecimal,
    val unitCostBase: BigDecimal
)

class PurchaseLineCalculator {
    fun calculate(
        quantityEntered: BigDecimal,
        lineTotal: BigDecimal,
        optionFactorToBase: BigDecimal
    ): CalculatedPurchaseLine {
        if (quantityEntered <= BigDecimal.ZERO) throw ValidationError.InvalidPurchaseQuantity
        if (lineTotal < BigDecimal.ZERO) throw ValidationError.InvalidPurchaseLineTotal
        if (optionFactorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

        val quantityBase = quantityEntered.multiply(optionFactorToBase, MathContext.DECIMAL128)
        if (quantityBase <= BigDecimal.ZERO) throw ValidationError.InvalidPurchaseQuantity

        val unitCostBase = lineTotal.divide(quantityBase, MathContext.DECIMAL128)

        return CalculatedPurchaseLine(
            quantityBase = quantityBase,
            unitCostBase = unitCostBase
        )
    }
}
