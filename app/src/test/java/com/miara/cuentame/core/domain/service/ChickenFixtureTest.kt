package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class ChickenFixtureTest {
    private val comparisonCalculator = CountComparisonCalculator()

    @Test
    fun `chicken fixture validation`() {
        val initialInventory = BigDecimal("20")
        val purchaseQuantity = BigDecimal("80") // 2 cases * 40 lb
        val recordedWaste = BigDecimal("3")
        val finalInventory = BigDecimal("65")
        
        val unclassifiedUsage = comparisonCalculator.calculateUnclassifiedUsage(
            initialInventory = initialInventory,
            purchases = purchaseQuantity,
            recordedWaste = recordedWaste,
            finalInventory = finalInventory
        )
        
        // 20 + 80 - 3 - 65 = 32
        assertThat(unclassifiedUsage.compareTo(BigDecimal("32"))).isEqualTo(0)
    }

    @Test
    fun `reversal negates everything correctly`() {
        // This is a placeholder to remind that I need to test service too
    }
}
