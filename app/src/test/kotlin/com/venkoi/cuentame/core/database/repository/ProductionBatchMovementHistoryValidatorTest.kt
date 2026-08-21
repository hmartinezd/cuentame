package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ProductionBatchMovementHistoryValidatorTest {

    private val validator = InventoryMovementValidator()
    private val historyValidator = InventoryMovementHistoryValidator(validator)

    @Test
    fun validHistory_passes() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1")
        
        historyValidator.validateCompleteHistory(listOf(m1, m2))
    }

    @Test
    fun reversalMissingTarget_throws() {
        val m1 = createMove("m1", "REVERSAL", "-10", "5", 1000, reversalOf = "missing")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1))
        }
    }

    @Test
    fun reversalTargetingAnotherReversal_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1")
        val m3 = createMove("m3", "REVERSAL", "10", "5", 1200, reversalOf = "m2")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2, m3))
        }
    }

    @Test
    fun duplicateReversalTarget_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1")
        val m3 = createMove("m3", "REVERSAL", "-10", "5", 1200, reversalOf = "m1")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2, m3))
        }
    }

    @Test
    fun reversalTargetingItself_throws() {
        val m1 = createMove("m1", "REVERSAL", "-10", "5", 1000, reversalOf = "m1")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1))
        }
    }

    @Test
    fun wrongReversalQuantity_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-9", "5", 1100, reversalOf = "m1")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun wrongReversalCost_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "6", 1100, reversalOf = "m1")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun wrongReversalTotalValue_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1").copy(totalValueSnapshot = "-49")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun wrongReversalArea_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1").copy(areaId = "other")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun wrongReversalSourceDocument_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1").copy(sourceDocumentId = "other")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun wrongReversalOperationId_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1").copy(sourceOperationId = "wrong")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun reversalEffectiveAtBeforeOriginal_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 900, reversalOf = "m1")
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    @Test
    fun reversalCreatedAtBeforeOriginal_throws() {
        val m1 = createMove("m1", "PURCHASE", "10", "5", 1000)
        val m2 = createMove("m2", "REVERSAL", "-10", "5", 1100, reversalOf = "m1").copy(createdAt = 900)
        
        assertThrows(ValidationError.MalformedInventoryMovementHistory::class.java) {
            historyValidator.validateCompleteHistory(listOf(m1, m2))
        }
    }

    private fun createMove(
        id: String,
        type: String,
        qty: String,
        cost: String?,
        time: Long,
        reversalOf: String? = null
    ) = InventoryMovementEntity(
        id = id,
        restaurantId = "r1",
        ingredientId = "i1",
        areaId = "a1",
        movementType = type,
        quantityBaseSigned = qty,
        unitCostBaseSnapshot = cost,
        totalValueSnapshot = cost?.let { BigDecimal(qty).multiply(BigDecimal(it)).toPlainString() },
        effectiveAt = time,
        sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
        sourceDocumentId = "doc1",
        sourceOperationId = if (type == "REVERSAL" && reversalOf != null) "reversal:$reversalOf" else "op_$id",
        sourceLineId = "line1",
        reversalOfMovementId = reversalOf,
        createdAt = time
    )
}
