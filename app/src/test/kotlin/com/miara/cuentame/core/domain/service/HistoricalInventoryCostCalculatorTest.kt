package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Test
import java.math.BigDecimal

class HistoricalInventoryCostCalculatorTest {
    private val calculator = HistoricalInventoryCostCalculator()

    @Test
    fun `calculate first purchase`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5.5", 1000)
        )
        val result = calculator.calculate(movements)
        assertThat(result.hasEstablishedCost).isTrue()
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("5.5"))).isEqualTo(0)
        assertThat(result.totalQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
    }

    @Test
    fun `calculate with existing inventory`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5", 1000),
            move("m2", InventoryMovementType.PURCHASE, "10", "7", 2000)
        )
        val result = calculator.calculate(movements)
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("6"))).isEqualTo(0)
    }

    @Test
    fun `calculate with production output`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5", 1000),
            move("m2", InventoryMovementType.PRODUCTION_OUTPUT, "10", "7", 2000)
        )
        val result = calculator.calculate(movements)
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("6"))).isEqualTo(0)
    }

    @Test
    fun `calculate excludes specific document`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5", 1000),
            move("m2", InventoryMovementType.PRODUCTION_OUTPUT, "10", "7", 2000, SourceDocumentType.PRODUCTION_BATCH, "batch-1")
        )
        val result = calculator.calculate(movements, excludedSourceDocument = SourceDocumentIdentity(SourceDocumentType.PRODUCTION_BATCH, "batch-1"))
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    private fun move(
        id: String,
        type: InventoryMovementType,
        qty: String,
        cost: String?,
        time: Long,
        docType: SourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
        docId: String = "d1"
    ) = HistoricalInventoryMovement(
        id = id,
        movementType = type,
        quantityBaseSigned = BigDecimal(qty),
        unitCostBaseSnapshot = cost?.let { BigDecimal(it) },
        sourceDocumentType = docType,
        sourceDocumentId = docId,
        effectiveAt = time,
        createdAt = time
    )
}
