package com.miara.cuentame.core.domain.service

import java.math.BigDecimal
import java.math.RoundingMode

enum class ReorderConfigurationStatus {
    READY, MISSING_PAR, MISSING_PURCHASE_UNIT, MISSING_SUPPLIER, AMBIGUOUS_SUPPLIER
}

data class ReorderCalculation(
    val needsReorder: Boolean,
    val quantityNeededBase: BigDecimal?,
    val purchaseUnitsSuggested: BigDecimal?,
    val suggestedPurchaseQuantityBase: BigDecimal?,
    val status: ReorderConfigurationStatus
)

object ReorderCalculator {
    fun calculate(
        currentQuantityBase: BigDecimal,
        parLevelBase: BigDecimal?,
        reorderPointBase: BigDecimal?,
        purchaseFactorToBase: BigDecimal?,
        hasSupplier: Boolean = true,
        ambiguousSupplier: Boolean = false
    ): ReorderCalculation {
        if (parLevelBase == null) return ReorderCalculation(false, null, null, null, ReorderConfigurationStatus.MISSING_PAR)
        require(parLevelBase >= BigDecimal.ZERO) { "Par must not be negative" }
        require(reorderPointBase == null || reorderPointBase >= BigDecimal.ZERO) { "Reorder point must not be negative" }
        require(reorderPointBase == null || reorderPointBase <= parLevelBase) { "Reorder point must not exceed par" }

        val triggered = reorderPointBase?.let { currentQuantityBase <= it } ?: (currentQuantityBase < parLevelBase)
        val needed = if (triggered) parLevelBase.subtract(currentQuantityBase).max(BigDecimal.ZERO) else BigDecimal.ZERO
        val validFactor = purchaseFactorToBase?.takeIf { it > BigDecimal.ZERO }
        val packages = if (triggered && validFactor != null) {
            needed.divide(validFactor, 0, RoundingMode.CEILING)
        } else null
        val purchaseBase = packages?.multiply(validFactor)
        val status = when {
            triggered && validFactor == null -> ReorderConfigurationStatus.MISSING_PURCHASE_UNIT
            ambiguousSupplier -> ReorderConfigurationStatus.AMBIGUOUS_SUPPLIER
            !hasSupplier -> ReorderConfigurationStatus.MISSING_SUPPLIER
            else -> ReorderConfigurationStatus.READY
        }
        return ReorderCalculation(triggered, needed, packages, purchaseBase, status)
    }
}
