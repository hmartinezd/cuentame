package com.venkoi.cuentame.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.InventoryMovementId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.dao.InventoryMovementDao
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.database.model.InventoryActivityRow
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryActivitySourceInfo
import com.venkoi.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import com.venkoi.cuentame.core.model.inventory.WasteReason
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class InventoryActivityRepositoryTest {

    private lateinit var repository: RoomInventoryActivityRepository
    private val movementDao: InventoryMovementDao = mockk()

    @Before
    fun setup() {
        repository = RoomInventoryActivityRepository(movementDao)
    }

    @Test
    fun `getActivityItem maps row to domain correctly`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = InventoryActivityRow(
            movement = InventoryMovementEntity(
                id = movementId,
                restaurantId = restaurantId.value,
                ingredientId = "ing-1",
                areaId = "area-1",
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "10.5",
                unitCostBaseSnapshot = "2.0",
                totalValueSnapshot = "21.0",
                effectiveAt = 1000L,
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "p1",
                sourceOperationId = "op1",
                sourceLineId = "l1",
                reversalOfMovementId = null,
                createdAt = 1000L
            ),
            ingredientName = "Ingredient 1",
            areaName = "Area 1",
            baseUnitSymbol = "lb",
            sourcePurchaseSupplierName = "Supplier 1",
            sourcePurchaseInvoiceNumber = "INV-1"
        )

        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))

        assertThat(result).isNotNull()
        assertThat(result!!.movement.quantityBaseSigned).isEqualTo(BigDecimal("10.5"))
        assertThat(result.ingredientName).isEqualTo("Ingredient 1")
        
        assertThat(result.sourceInfo).isInstanceOf(InventoryActivitySourceInfo.Purchase::class.java)
        val info = result.sourceInfo as InventoryActivitySourceInfo.Purchase
        assertThat(info.supplierName).isEqualTo("Supplier 1")
        assertThat(info.invoiceNumber).isEqualTo("INV-1")
        assertThat(info.isResolved).isFalse()
    }

    @Test
    fun `getActivityItem maps waste reason correctly`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.WASTE_EVENT, "w1").copy(
            sourceWasteReason = WasteReason.SPOILED.name
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))!!
        val info = result.sourceInfo as InventoryActivitySourceInfo.Waste
        assertThat(info.reason).isEqualTo(WasteReason.SPOILED)
    }

    @Test
    fun `getActivityItem maps production status correctly`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.PRODUCTION_BATCH, "b1").copy(
            sourceProductionStatus = DocumentStatus.POSTED.name
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))!!
        val info = result.sourceInfo as InventoryActivitySourceInfo.Production
        assertThat(info.status).isEqualTo(DocumentStatus.POSTED)
    }

    @Test
    fun `getActivityItem returns null for other restaurant`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns null

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNull()
    }

    @Test
    fun `resolveSourceTarget returns typed target only when resolved`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.PURCHASE_RECEIPT, "p1")
        
        // Unresolved
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row
        val itemUnresolved = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))!!
        assertThat(repository.resolveSourceTarget(itemUnresolved)).isEqualTo(InventoryActivitySourceTarget.Unavailable)

        // Resolved
        val resolvedRow = row.copy(sourcePurchaseResolvedId = "p1")
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns resolvedRow
        val itemResolved = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))!!
        val target = repository.resolveSourceTarget(itemResolved)
        assertThat(target).isInstanceOf(InventoryActivitySourceTarget.Purchase::class.java)
    }

    @Test
    fun `getActivityItem fails on malformed BigDecimal`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = InventoryActivityRow(
            movement = createBaseMovement(movementId).copy(quantityBaseSigned = "invalid"),
            ingredientName = "Ing",
            areaName = "Area",
            baseUnitSymbol = "lb"
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        try {
            repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
            org.junit.Assert.fail("Should have thrown NumberFormatException")
        } catch (e: NumberFormatException) {
            // Success
        }
    }

    @Test
    fun `getActivityItem handles malformed Waste reason safely`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.WASTE_EVENT, "w1").copy(
            sourceWasteReason = "MALFORMED_REASON"
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNotNull()
        val info = result!!.sourceInfo as InventoryActivitySourceInfo.Waste
        assertThat(info.reason).isEqualTo(WasteReason.UNKNOWN)
    }

    @Test
    fun `getActivityItem handles malformed Production status safely`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.PRODUCTION_BATCH, "b1").copy(
            sourceProductionStatus = "MALFORMED_STATUS"
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNotNull()
        val info = result!!.sourceInfo as InventoryActivitySourceInfo.Production
        assertThat(info.status).isEqualTo(DocumentStatus.UNKNOWN)
    }

    @Test
    fun `getActivityItem handles malformed source-document type safely`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.PURCHASE_RECEIPT, "p1").copy(
            movement = createBaseMovement(movementId).copy(sourceDocumentType = "MALFORMED_DOC_TYPE")
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNotNull()
        assertThat(result!!.movement.sourceDocumentType).isEqualTo(SourceDocumentType.UNKNOWN)
        assertThat(result.sourceInfo).isInstanceOf(InventoryActivitySourceInfo.Other::class.java)
        val info = result.sourceInfo as InventoryActivitySourceInfo.Other
        assertThat(info.sourceDocumentType).isEqualTo(SourceDocumentType.UNKNOWN)
    }

    @Test
    fun `getActivityItem handles malformed movement type safely`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.PURCHASE_RECEIPT, "p1").copy(
            movement = createBaseMovement(movementId).copy(movementType = "MALFORMED_TYPE")
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNotNull()
        assertThat(result!!.movement.movementType).isEqualTo(InventoryMovementType.UNKNOWN)
    }

    @Test
    fun `getActivityItem handles blank enum values safely`() = runBlocking {
        val movementId = "m1"
        val restaurantId = RestaurantId("rest-1")
        val row = createActivityRow(movementId, SourceDocumentType.WASTE_EVENT, "w1").copy(
            sourceWasteReason = "  "
        )
        coEvery { movementDao.getInventoryActivityRow(restaurantId.value, movementId) } returns row

        val result = repository.getActivityItem(restaurantId, InventoryMovementId(movementId))
        assertThat(result).isNotNull()
        val info = result!!.sourceInfo as InventoryActivitySourceInfo.Waste
        assertThat(info.reason).isEqualTo(WasteReason.UNKNOWN)
    }

    private fun createActivityRow(id: String, type: SourceDocumentType, docId: String) = InventoryActivityRow(
        movement = createBaseMovement(id).copy(sourceDocumentType = type.name, sourceDocumentId = docId),
        ingredientName = "Ing",
        areaName = "Area",
        baseUnitSymbol = "lb"
    )

    private fun createBaseMovement(id: String) = InventoryMovementEntity(
        id = id,
        restaurantId = "r",
        ingredientId = "i",
        areaId = "a",
        movementType = "PURCHASE",
        quantityBaseSigned = "1",
        unitCostBaseSnapshot = null,
        totalValueSnapshot = null,
        effectiveAt = 0L,
        sourceDocumentType = "PURCHASE_RECEIPT",
        sourceDocumentId = "d",
        sourceOperationId = "o",
        sourceLineId = null,
        reversalOfMovementId = null,
        createdAt = 0L
    )
}
