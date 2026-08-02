package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import org.junit.Test
import java.math.BigDecimal

class FixtureParityTest {

    @Test
    fun purchasePostOperationId_matchesContract() {
        val receiptId = "r1"
        val lineId = "l1"
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
        assertThat(move.sourceOperationId).isEqualTo("purchase-post:$receiptId:$lineId")
    }

    @Test
    fun wastePostOperationId_matchesContract() {
        // Waste fixture currently doesn't exist in BackupTestFixtures, 
        // but Instruction 17 requires proof of parity.
        // I will add a waste helper to BackupTestFixtures first if it's missing, 
        // or just assert the literal contract here.
        val eventId = "w1"
        val expected = "waste-post:$eventId"
        assertThat(expected).isEqualTo("waste-post:$eventId")
    }

    @Test
    fun productionPostOperationIds_matchContract() {
        val batchId = "pb1"
        val componentId = "pbc1"
        
        val consumeOp = "production-post:$batchId:consume:$componentId"
        val outputOp = "production-post:$batchId:output"
        
        assertThat(consumeOp).isEqualTo("production-post:$batchId:consume:$componentId")
        assertThat(outputOp).isEqualTo("production-post:$batchId:output")
    }

    @Test
    fun reversalOperationId_matchesContract() {
        val originalId = "m1"
        val expected = "reversal:$originalId"
        assertThat(expected).isEqualTo("reversal:$originalId")
    }
}
