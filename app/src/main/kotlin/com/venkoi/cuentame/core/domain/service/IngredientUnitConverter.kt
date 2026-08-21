package com.venkoi.cuentame.core.domain.service

import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import java.math.BigDecimal
import java.math.MathContext

class IngredientUnitConverter {
    fun toBase(
        quantity: BigDecimal,
        option: IngredientUnitOption
    ): BigDecimal {
        if (option.factorToBase <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Unit factor must be greater than zero")
        }
        return quantity.multiply(option.factorToBase, MathContext.DECIMAL128)
    }

    fun fromBase(
        quantityBase: BigDecimal,
        option: IngredientUnitOption
    ): BigDecimal {
        if (option.factorToBase <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Unit factor must be greater than zero")
        }
        return quantityBase.divide(option.factorToBase, MathContext.DECIMAL128)
    }
}
