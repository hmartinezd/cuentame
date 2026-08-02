package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import org.junit.Test
import java.math.BigDecimal

class FixtureParityTest {

    @Test
    fun purchasePostOperationId_matchesContract() {
        val receiptId = "r1"
        val lineId = "l1"
        
        // 1. Call canonical factory
        val expected = InventoryMovementOperationIds.purchasePost(receiptId, lineId)
        
        // 2. Build fixture
        val snapshot = BackupTestFixtures.addPostedPurchase(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            receiptId = receiptId,
            lineId = lineId,
            movementId = "m1",
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        
        // 3. Assert fixture uses factory result
        assertThat(move.sourceOperationId).isEqualTo(expected)
        
        // 4. Assert result remains the frozen literal
        assertThat(expected).isEqualTo("purchase-post:r1:l1")
    }

    @Test
    fun wastePostOperationId_matchesContract() {
        val eventId = "w1"
        
        val expected = InventoryMovementOperationIds.wastePost(eventId)
        
        val snapshot = BackupTestFixtures.addPostedWaste(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            eventId = eventId,
            movementId = "m1",
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("waste-post:w1")
    }

    @Test
    fun productionPostOperationIds_matchContract() {
        val batchId = "pb1"
        
        val consumeOp = InventoryMovementOperationIds.productionConsumption(batchId, "pbc1")
        val outputOp = InventoryMovementOperationIds.productionOutput(batchId)
        
        val snapshot = BackupTestFixtures.addPostedProduction(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            batchId = batchId,
            outputMovementId = "m-out",
            outputIngredientId = "i-out",
            outputAreaId = "a-out",
            outputOptionId = "o-out",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(outputOp)
        
        assertThat(consumeOp).isEqualTo("production-post:pb1:consume:pbc1")
        assertThat(outputOp).isEqualTo("production-post:pb1:output")
    }

    @Test
    fun reversalOperationId_matchesContract() {
        val originalId = "m1"
        val expected = InventoryMovementOperationIds.reversal(originalId)
        
        assertThat(expected).isEqualTo("reversal:m1")
    }
}
