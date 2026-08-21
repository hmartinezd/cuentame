package com.venkoi.restaurantops.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import org.junit.Assert.assertThrows

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
    @Test fun negativeParIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { calc("0", "-1") }
    }
    @Test fun negativeReorderPointIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { calc("0", "10", "-1") }
    }
    @Test fun reorderPointBelowParIsAccepted() = assertThat(calc("0", "10", "5").needsReorder).isTrue()
    @Test fun reorderPointEqualToParIsAccepted() = assertThat(calc("10", "10", "10").needsReorder).isTrue()
    @Test fun reorderPointAboveParIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { calc("0", "10", "11") }
    }
    @Test fun nullParAndPointAreAcceptedAsMissingSetup() {
        val result = calc("0", null, null)
        assertThat(result.status).isEqualTo(ReorderConfigurationStatus.MISSING_PAR)
        assertThat(result.needsReorder).isFalse()
    }
    @Test fun parWithoutReorderPointIsAccepted() = assertThat(calc("0", "10", null).needsReorder).isTrue()
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
