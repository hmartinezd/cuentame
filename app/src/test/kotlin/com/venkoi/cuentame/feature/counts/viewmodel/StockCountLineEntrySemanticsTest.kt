package com.venkoi.cuentame.feature.counts.viewmodel

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

class StockCountLineEntrySemanticsTest {
    private fun saved(quantity: String = "8") = StockCountLineEntry(
        ingredientId = "beef",
        ingredientName = "Beef",
        categoryName = null,
        unitId = "lb",
        unitName = "lb",
        factorToBase = BigDecimal.ONE,
        baseUnitName = "lb",
        quantityText = quantity,
        lineId = "line-beef",
        hasUserEdit = true,
        editRevision = 0,
        savedRevision = 0
    )

    @Test fun `saved valid observation is counted`() {
        assertThat(saved().isCountedForProgress).isTrue()
    }

    @Test fun `invalid edit does not inherit counted state from persisted line`() {
        assertThat(saved("-2").copy(editRevision = 1).isCountedForProgress).isFalse()
    }

    @Test fun `explicit zero remains counted while pending`() {
        assertThat(saved("0").copy(editRevision = 1).isCountedForProgress).isTrue()
    }

    @Test fun `blank current observation is uncounted`() {
        assertThat(saved("").copy(editRevision = 1).isCountedForProgress).isFalse()
    }
}
