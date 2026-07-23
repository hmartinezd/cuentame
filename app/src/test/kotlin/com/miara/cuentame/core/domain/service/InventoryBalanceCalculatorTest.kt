package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class InventoryBalanceCalculatorTest {
    private val calculator = InventoryBalanceCalculator()

    @Test
    fun `calculateBalance sums quantities`() {
        val movements = listOf(
            createMovement(BigDecimal("10")),
            createMovement(BigDecimal("-3")),
            createMovement(BigDecimal("5.5"))
        )
        val result = calculator.calculateBalance(movements)
        assertThat(result.compareTo(BigDecimal("12.5"))).isEqualTo(0)
    }

    @Test
    fun `calculateBalance handles empty list`() {
        assertThat(calculator.calculateBalance(emptyList())).isEqualTo(BigDecimal.ZERO)
    }

    private fun createMovement(quantity: BigDecimal) = InventoryMovement(
        id = InventoryMovementId("1"),
        restaurantId = RestaurantId("1"),
        ingredientId = IngredientId("1"),
        areaId = InventoryAreaId("1"),
        movementType = InventoryMovementType.PURCHASE,
        quantityBaseSigned = quantity,
        effectiveAt = Instant.now(),
        sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
        sourceDocumentId = "1",
        sourceOperationId = "1",
        createdAt = Instant.now()
    )
}
