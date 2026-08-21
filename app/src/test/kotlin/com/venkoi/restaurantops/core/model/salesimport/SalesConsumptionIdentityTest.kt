package com.venkoi.restaurantops.core.model.salesimport

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementOperationIds
import org.junit.Test

class SalesConsumptionIdentityTest {
    @Test fun `transaction identity is deterministic and delimiter collision safe`() {
        assertThat(SalesTransactionSourceIdentity.encode("a:b", "c"))
            .isNotEqualTo(SalesTransactionSourceIdentity.encode("a", "b:c"))
        assertThat(SalesTransactionSourceIdentity.encode("a:b", "c"))
            .isEqualTo(SalesTransactionSourceIdentity.encode("a:b", "c"))
    }

    @Test fun `operation identity is deterministic and component specific`() {
        val first = InventoryMovementOperationIds.salesConsumption("line:1", "component")
        assertThat(first).isEqualTo(InventoryMovementOperationIds.salesConsumption("line:1", "component"))
        assertThat(first).isNotEqualTo(InventoryMovementOperationIds.salesConsumption("line", "1:component"))
        assertThat(first).isNotEqualTo(InventoryMovementOperationIds.salesConsumption("line:1", "other"))
    }
}
