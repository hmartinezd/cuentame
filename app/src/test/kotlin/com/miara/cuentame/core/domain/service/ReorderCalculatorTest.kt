package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class ReorderCalculatorTest {
    private fun calc(current: String, par: String? = "20", point: String? = null, factor: String? = "5") =
        ReorderCalculator.calculate(BigDecimal(current), par?.let(::BigDecimal), point?.let(::BigDecimal), factor?.let(::BigDecimal))
    private fun assertDecimal(actual: BigDecimal?, expected: String) = assertThat(actual?.compareTo(BigDecimal(expected))).isEqualTo(0)

    @Test fun belowParNeedsDifference() = assertDecimal(calc("7").quantityNeededBase, "13")
    @Test fun atParDoesNotReorder() = assertThat(calc("20").needsReorder).isFalse()
    @Test fun aboveParDoesNotReorder() = assertThat(calc("25").needsReorder).isFalse()
    @Test fun aboveReorderPointDoesNotReorder() = assertThat(calc("9", point = "8").needsReorder).isFalse()
    @Test fun atReorderPointReorders() = assertDecimal(calc("8", point = "8").quantityNeededBase, "12")
    @Test fun negativeInventoryIsNotClamped() = assertDecimal(calc("-2", "10").quantityNeededBase, "12")
    @Test fun zeroInventoryNeedsPar() = assertDecimal(calc("0", "10").quantityNeededBase, "10")
    @Test fun missingParIsExplicit() = assertThat(calc("3", null).status).isEqualTo(ReorderConfigurationStatus.MISSING_PAR)
    @Test fun exactPackageDivision() = assertDecimal(calc("5", "20").purchaseUnitsSuggested, "3")
    @Test fun fractionalPackageDivisionRoundsUp() = assertDecimal(calc("7", "20").purchaseUnitsSuggested, "3")
    @Test fun smallNeedBuysOnePackage() = assertDecimal(calc("19", "20").purchaseUnitsSuggested, "1")
    @Test fun decimalFactorIsPrecise() = assertDecimal(calc("8.9", "10", factor = "0.375").suggestedPurchaseQuantityBase, "1.125")
    @Test fun invalidFactorKeepsBaseRecommendation() {
        val result = calc("7", factor = "0")
        assertDecimal(result.quantityNeededBase, "13")
        assertThat(result.purchaseUnitsSuggested).isNull()
        assertThat(result.status).isEqualTo(ReorderConfigurationStatus.MISSING_PURCHASE_UNIT)
    }
}
