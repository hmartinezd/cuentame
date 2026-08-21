package com.venkoi.cuentame.feature.counts.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StockCountFocusIndexTest {
    @Test fun `offscreen row index includes all preceding non-row content`() {
        assertThat(
            countRowAbsoluteIndex(
                rowIndex = 12,
                searchResultCount = 3,
                hasSearchSection = true,
                archivedWarningCount = 2
            )
        ).isEqualTo(20)
    }

    @Test fun `uncounted filtered rows retain deterministic row-relative mapping`() {
        assertThat(countRowAbsoluteIndex(7, 0, false, 1)).isEqualTo(10)
    }
}
