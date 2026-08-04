package com.miara.cuentame.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.model.InventoryActivityRow
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceInfo
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

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
        val row = InventoryActivityRow(
            movement = InventoryMovementEntity(
                id = movementId,
                restaurantId = "rest-1",
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

        coEvery { movementDao.getInventoryActivityRow(movementId) } returns row

        val result = repository.getActivityItem(InventoryMovementId(movementId))

        assertThat(result).isNotNull()
        assertThat(result!!.movement.quantityBaseSigned).isEqualTo(BigDecimal("10.5"))
        assertThat(result.ingredientName).isEqualTo("Ingredient 1")
        
        // Repository should no longer generate strings
        assertThat(result.sourceInfo).isInstanceOf(InventoryActivitySourceInfo.Purchase::class.java)
        val info = result.sourceInfo as InventoryActivitySourceInfo.Purchase
        assertThat(info.supplierName).isEqualTo("Supplier 1")
        assertThat(info.invoiceNumber).isEqualTo("INV-1")
        assertThat(info.isResolved).isFalse() // markers not set in this test row
    }

    @Test
    fun `resolveSourceTarget returns typed target only when resolved`() = runBlocking {
        val movementId = "m1"
        val row = createActivityRow(movementId, SourceDocumentType.PURCHASE_RECEIPT, "p1")
        
        // Unresolved
        coEvery { movementDao.getInventoryActivityRow(movementId) } returns row
        val itemUnresolved = repository.getActivityItem(InventoryMovementId(movementId))!!
        assertThat(repository.resolveSourceTarget(itemUnresolved)).isEqualTo(InventoryActivitySourceTarget.Unavailable)

        // Resolved
        val resolvedRow = row.copy(sourcePurchaseResolvedId = "p1")
        coEvery { movementDao.getInventoryActivityRow(movementId) } returns resolvedRow
        val itemResolved = repository.getActivityItem(InventoryMovementId(movementId))!!
        val target = repository.resolveSourceTarget(itemResolved)
        assertThat(target).isInstanceOf(InventoryActivitySourceTarget.Purchase::class.java)
    }

    @Test
    fun `getActivityItem fails on malformed BigDecimal`() = runBlocking {
        val movementId = "m1"
        val row = InventoryActivityRow(
            movement = createBaseMovement(movementId).copy(quantityBaseSigned = "invalid"),
            ingredientName = "Ing",
            areaName = "Area",
            baseUnitSymbol = "lb"
        )
        coEvery { movementDao.getInventoryActivityRow(movementId) } returns row

        val exception = try {
            repository.getActivityItem(InventoryMovementId(movementId))
            null
        } catch (e: NumberFormatException) {
            e
        }
        assertThat(exception).isNotNull()
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
