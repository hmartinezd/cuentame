package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class InventoryMovementServiceTest {

    private val idGenerator = object : IdGenerator {
        override fun newId() = "rev_1"
    }
    private val fixedTime = Instant.parse("2024-01-01T00:00:00Z")
    private val timeProvider = object : TimeProvider {
        override fun now() = fixedTime
    }

    private val service = InventoryMovementService(idGenerator, timeProvider)

    @Test
    fun `createReversal returns negative quantity and references original`() {
        val original = createMovement(BigDecimal("10"))
        val reversal = service.createReversal(original)

        assertThat(reversal.quantityBaseSigned.compareTo(BigDecimal("-10"))).isEqualTo(0)
        assertThat(reversal.reversalOfMovementId).isEqualTo(original.id)
        assertThat(reversal.movementType).isEqualTo(InventoryMovementType.REVERSAL)
        assertThat(reversal.createdAt).isEqualTo(fixedTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot reverse a reversal`() {
        val original = createMovement(BigDecimal("10")).copy(movementType = InventoryMovementType.REVERSAL)
        service.createReversal(original)
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
