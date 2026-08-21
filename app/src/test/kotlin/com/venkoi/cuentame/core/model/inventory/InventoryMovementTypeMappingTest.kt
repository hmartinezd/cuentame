package com.venkoi.cuentame.core.model.inventory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InventoryMovementTypeMappingTest {

    @Test
    fun `toInventoryActivityCategory covers all movement types`() {
        InventoryMovementType.entries.forEach { type ->
            val category = type.toInventoryActivityCategory()
            assertThat(category).isNotNull()
        }
    }

    @Test
    fun `toInventoryActivityCategory maps correctly`() {
        assertThat(InventoryMovementType.PURCHASE.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.PURCHASE)
        assertThat(InventoryMovementType.WASTE.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.WASTE)
        assertThat(InventoryMovementType.COUNT_ADJUSTMENT.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.STOCK_COUNT)
        assertThat(InventoryMovementType.REVERSAL.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.REVERSAL)
        assertThat(InventoryMovementType.PRODUCTION_CONSUMPTION.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.PRODUCTION_CONSUMPTION)
        assertThat(InventoryMovementType.PRODUCTION_OUTPUT.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.PRODUCTION_OUTPUT)
        assertThat(InventoryMovementType.MANUAL_ADJUSTMENT.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.OTHER)
        assertThat(InventoryMovementType.OPENING_BALANCE.toInventoryActivityCategory()).isEqualTo(InventoryActivityCategory.OTHER)
    }
}
