package com.miara.cuentame.core.domain.service

import java.math.BigDecimal
import java.math.MathContext

class WeightedAverageCostCalculator {
    fun calculate(
        currentQuantity: BigDecimal,
        currentAverageCost: BigDecimal,
        purchaseQuantity: BigDecimal,
        purchaseUnitCost: BigDecimal
    ): BigDecimal {
        if (purchaseQuantity <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Purchase quantity must be greater than zero")
        }
        if (purchaseUnitCost < BigDecimal.ZERO) {
            throw IllegalArgumentException("Purchase unit cost cannot be negative")
        }

        if (currentQuantity <= BigDecimal.ZERO) {
            return purchaseUnitCost
        }

        val currentTotalValue = currentQuantity.multiply(currentAverageCost, MathContext.DECIMAL128)
        val purchaseTotalValue = purchaseQuantity.multiply(purchaseUnitCost, MathContext.DECIMAL128)
        val newTotalQuantity = currentQuantity.add(purchaseQuantity)
        
        return currentTotalValue.add(purchaseTotalValue).divide(newTotalQuantity, MathContext.DECIMAL128)
    }
}
