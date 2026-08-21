package com.venkoi.cuentame.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import java.math.BigDecimal
import org.junit.Test

class StockCountCandidateSemanticsTest {
    @Test fun `non-default area with nonzero balance is countable`() {
        assertThat(
            isStockCountCandidate(
                defaultAreaId = InventoryAreaId("prep"),
                areaId = InventoryAreaId("walk-in"),
                balance = BigDecimal("2")
            )
        ).isTrue()
    }

    @Test fun `candidate identity remains per area`() {
        val cheeseRows = listOf(InventoryAreaId("walk-in"), InventoryAreaId("bar"))
            .count { isStockCountCandidate(it, it, BigDecimal.ZERO) }
        assertThat(cheeseRows).isEqualTo(2)
    }
}
