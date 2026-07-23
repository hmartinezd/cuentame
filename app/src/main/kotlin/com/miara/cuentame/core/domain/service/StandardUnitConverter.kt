package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import java.math.BigDecimal
import java.math.MathContext

class StandardUnitConverter {
    fun convert(
        quantity: BigDecimal,
        from: UnitOfMeasure,
        to: UnitOfMeasure
    ): BigDecimal {
        if (from.dimension != to.dimension) {
            throw IllegalArgumentException("Incompatible unit dimensions: ${from.dimension} and ${to.dimension}")
        }
        if (from.factorToCanonical <= BigDecimal.ZERO || to.factorToCanonical <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Unit factors must be greater than zero")
        }

        // canonical quantity = quantity * from.factorToCanonical
        val canonicalQuantity = quantity.multiply(from.factorToCanonical, MathContext.DECIMAL128)
        // result = canonical quantity / to.factorToCanonical
        return canonicalQuantity.divide(to.factorToCanonical, MathContext.DECIMAL128)
    }
}
