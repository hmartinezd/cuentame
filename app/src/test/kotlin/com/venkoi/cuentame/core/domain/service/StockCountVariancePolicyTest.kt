package com.venkoi.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class StockCountVariancePolicyTest {
    @Test fun `small variance does not warn`() {
        assertThat(hasLargeCountVariance(BigDecimal("100"), BigDecimal("95"))).isFalse()
    }

    @Test fun `large and zero counts warn against positive expected inventory`() {
        assertThat(hasLargeCountVariance(BigDecimal("100"), BigDecimal("40"))).isTrue()
        assertThat(hasLargeCountVariance(BigDecimal("100"), BigDecimal.ZERO)).isTrue()
    }

    @Test fun `opening balance does not use percentage warning`() {
        assertThat(hasLargeCountVariance(null, BigDecimal("1000"))).isFalse()
    }
}
