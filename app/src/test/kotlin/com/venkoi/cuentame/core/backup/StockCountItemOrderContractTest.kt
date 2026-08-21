package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract
import org.junit.Test

class StockCountItemOrderContractTest {
    @Test fun `schema 10 excludes stock count item order`() {
        assertThat(BackupFormatV1Contract.expectedTablesForSchema(10))
            .doesNotContain("stock_count_item_order")
    }

    @Test fun `schema 11 includes stock count item order as configuration`() {
        assertThat(BackupFormatV1Contract.expectedTablesForSchema(11))
            .contains("stock_count_item_order")
        assertThat(BackupFormatV1Contract.DERIVED_TABLES)
            .doesNotContain("stock_count_item_order")
    }
}
