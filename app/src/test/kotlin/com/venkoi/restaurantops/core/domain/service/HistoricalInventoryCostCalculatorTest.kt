package com.venkoi.restaurantops.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import org.junit.Test
import java.math.BigDecimal

class HistoricalInventoryCostCalculatorTest {
    private val calculator = HistoricalInventoryCostCalculator()

    @Test
    fun `calculate first purchase`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5.5", 1000)
        )
        val calculationResult = calculator.calculate(movements)
        assertThat(calculationResult).isInstanceOf(HistoricalInventoryCostCalculationResult.Success::class.java)
        val result = (calculationResult as HistoricalInventoryCostCalculationResult.Success).value

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
        val calculationResult = calculator.calculate(movements)
        val result = (calculationResult as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("6"))).isEqualTo(0)
    }

    @Test
    fun `calculate with production output`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5", 1000),
            move("m2", InventoryMovementType.PRODUCTION_OUTPUT, "10", "7", 2000)
        )
        val calculationResult = calculator.calculate(movements)
        val result = (calculationResult as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("6"))).isEqualTo(0)
    }

    @Test
    fun `calculate excludes specific document`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "5", 1000),
            move("m2", InventoryMovementType.PRODUCTION_OUTPUT, "10", "7", 2000, SourceDocumentType.PRODUCTION_BATCH, "batch-1")
        )
        val calculationResult = calculator.calculate(movements, excludedSourceDocument = SourceDocumentIdentity(SourceDocumentType.PRODUCTION_BATCH, "batch-1"))
        val result = (calculationResult as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.averageUnitCostBase!!.compareTo(BigDecimal("5"))).isEqualTo(0)
    }

    @Test
    fun `calculate with temporal boundary - included`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 1000, createdAt = 1000)
        )
        val boundary = HistoricalInventoryCostBoundary(effectiveAtInclusive = 1000, createdAtInclusive = 1000)
        val result = (calculator.calculate(movements, boundary = boundary) as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.totalQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
    }

    @Test
    fun `calculate with temporal boundary - excluded by effectiveAt`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 1001, createdAt = 1000)
        )
        val boundary = HistoricalInventoryCostBoundary(effectiveAtInclusive = 1000, createdAtInclusive = 1000)
        val result = (calculator.calculate(movements, boundary = boundary) as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.totalQuantityBase).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculate with temporal boundary - excluded by createdAt`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 500, createdAt = 1001)
        )
        val boundary = HistoricalInventoryCostBoundary(effectiveAtInclusive = 1000, createdAtInclusive = 1000)
        val result = (calculator.calculate(movements, boundary = boundary) as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.totalQuantityBase).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculate with reversal - included`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 500),
            move("m2", InventoryMovementType.REVERSAL, "-10", "10", 600, revOfId = "m1")
        )
        val result = (calculator.calculate(movements) as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.totalQuantityBase).isEqualTo(BigDecimal.ZERO)
        assertThat(result.hasEstablishedCost).isFalse()
    }

    @Test
    fun `calculate with reversal - future reversal excluded`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 500, createdAt = 500),
            move("m2", InventoryMovementType.REVERSAL, "-10", "10", 1500, createdAt = 1500, revOfId = "m1")
        )
        val boundary = HistoricalInventoryCostBoundary(effectiveAtInclusive = 1000, createdAtInclusive = 1000)
        val result = (calculator.calculate(movements, boundary = boundary) as HistoricalInventoryCostCalculationResult.Success).value
        assertThat(result.totalQuantityBase.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(result.hasEstablishedCost).isTrue()
    }

    @Test
    fun `calculate fails on missing reversal target`() {
        val movements = listOf(
            move("m2", InventoryMovementType.REVERSAL, "-10", "10", 600, revOfId = "m1")
        )
        val result = calculator.calculate(movements)
        assertThat(result).isInstanceOf(HistoricalInventoryCostCalculationResult.Failure::class.java)
        assertThat((result as HistoricalInventoryCostCalculationResult.Failure).reason).isEqualTo(HistoricalInventoryCostFailure.ReversalTargetNotFound)
    }

    @Test
    fun `calculate fails on duplicate reversal target`() {
        val movements = listOf(
            move("m1", InventoryMovementType.PURCHASE, "10", "10", 500),
            move("m2", InventoryMovementType.REVERSAL, "-10", "10", 600, revOfId = "m1"),
            move("m3", InventoryMovementType.REVERSAL, "-10", "10", 700, revOfId = "m1")
        )
        val result = calculator.calculate(movements)
        assertThat(result).isInstanceOf(HistoricalInventoryCostCalculationResult.Failure::class.java)
        assertThat((result as HistoricalInventoryCostCalculationResult.Failure).reason).isEqualTo(HistoricalInventoryCostFailure.DuplicateReversalTarget)
    }

    private fun move(
        id: String,
        type: InventoryMovementType,
        qty: String,
        cost: String?,
        time: Long,
        docType: SourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
        docId: String = "d1",
        createdAt: Long = time,
        revOfId: String? = null
    ) = HistoricalInventoryMovement(
        id = id,
        movementType = type,
        quantityBaseSigned = BigDecimal(qty),
        unitCostBaseSnapshot = cost?.let { BigDecimal(it) },
        sourceDocumentType = docType,
        sourceDocumentId = docId,
        effectiveAt = time,
        createdAt = createdAt,
        reversalOfMovementId = revOfId
    )
}
