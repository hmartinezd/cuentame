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
        
        val expected = InventoryMovementOperationIds.purchasePost(receiptId, lineId)
        
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
        assertThat(move.sourceOperationId).isEqualTo(expected)
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
    fun productionOutputOperationId_matchesContract() {
        val batchId = "pb1"
        val expected = InventoryMovementOperationIds.productionOutput(batchId)
        
        val snapshot = BackupTestFixtures.addPostedProduction(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            batchId = batchId,
            recipeId = "rec1",
            recipeComponentId = "rc1",
            componentId = "pbc1",
            componentIngredientId = "i-comp",
            componentAreaId = "a-out",
            componentOptionId = "o-comp",
            componentQuantityBase = BigDecimal.ONE,
            componentUnitCostBase = BigDecimal.ONE,
            consumptionMovementId = "m-consume",
            outputMovementId = "m-out",
            outputIngredientId = "i-out",
            outputAreaId = "a-out",
            outputOptionId = "o-out",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.find { it.movementType == "PRODUCTION_OUTPUT" }!!
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("production-post:pb1:output")
    }

    @Test
    fun productionConsumptionOperationId_matchesContract() {
        val batchId = "pb1"
        val componentId = "pbc1"
        val expected = InventoryMovementOperationIds.productionConsumption(batchId, componentId)
        
        val snapshot = BackupTestFixtures.addProductionConsumption(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            batchId = batchId,
            componentId = componentId,
            movementId = "m-consume",
            ingredientId = "i-comp",
            areaId = "a-comp",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("production-post:pb1:consume:pbc1")
    }

    @Test
    fun reversalOperationId_matchesContract() {
        val originalId = "m1"
        val expected = InventoryMovementOperationIds.reversal(originalId)
        
        val snapshot = BackupTestFixtures.addPostedPurchase(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            receiptId = "r1",
            lineId = "l1",
            movementId = originalId,
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val snapshotWithReversal = BackupTestFixtures.addReversal(
            snapshot = snapshot,
            originalMovementId = originalId,
            reversalMovementId = "m2",
            effectiveAt = 2000L,
            createdAt = 2000L
        )
        
        val reversalMove = snapshotWithReversal.inventoryMovements.last()
        assertThat(reversalMove.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("reversal:m1")
    }
}
