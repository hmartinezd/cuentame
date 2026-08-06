package com.miara.cuentame.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class WasteMovementHistoryValidatorTest {

    private val movementValidator = InventoryMovementValidator()
    private val validator = WasteMovementHistoryValidator(movementValidator)

    private fun createBaseEvent() = WasteEventEntity(
        id = "event-1",
        restaurantId = "rest-1",
        ingredientId = "ing-1",
        areaId = "area-1",
        ingredientUnitOptionId = "opt-1",
        quantityEntered = "10.0",
        quantityBase = "10.0",
        reason = "SPOILED",
        effectiveAt = 1000L,
        notes = null,
        attachmentPath = null,
        attachmentDisplayName = null,
        status = DocumentStatus.POSTED.name,
        createdAt = 500L,
        updatedAt = 2000L,
        postedAt = 2000L,
        voidedAt = null
    )

    private fun createBaseMovement() = InventoryMovementEntity(
        id = "mov-1",
        restaurantId = "rest-1",
        ingredientId = "ing-1",
        areaId = "area-1",
        movementType = InventoryMovementType.WASTE.name,
        quantityBaseSigned = "-10.0",
        unitCostBaseSnapshot = "2.0",
        totalValueSnapshot = "-20.0",
        effectiveAt = 1000L,
        sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
        sourceDocumentId = "event-1",
        sourceLineId = "event-1",
        sourceOperationId = "waste-post:event-1",
        reversalOfMovementId = null,
        createdAt = 2000L
    )

    @Test
    fun validPostedHistory_passes() {
        validator.validatePostedHistory(createBaseEvent(), listOf(createBaseMovement()))
    }

    @Test
    fun positiveWasteQuantity_throws() {
        val movement = createBaseMovement().copy(quantityBaseSigned = "10.0")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun wrongRestaurant_throws() {
        val movement = createBaseMovement().copy(restaurantId = "other")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun costWithoutTotal_throws() {
        val movement = createBaseMovement().copy(totalValueSnapshot = null)
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun incorrectTotalEquation_throws() {
        val movement = createBaseMovement().copy(totalValueSnapshot = "-15.0")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun validVoidedHistory_passes() {
        val event = createBaseEvent().copy(
            status = DocumentStatus.VOIDED.name,
            voidedAt = 3000L,
            updatedAt = 3000L
        )
        val original = createBaseMovement()
        val reversal = InventoryMovementEntity(
            id = "rev-1",
            restaurantId = "rest-1",
            ingredientId = "ing-1",
            areaId = "area-1",
            movementType = InventoryMovementType.REVERSAL.name,
            quantityBaseSigned = "10.0",
            unitCostBaseSnapshot = "2.0",
            totalValueSnapshot = "20.0",
            effectiveAt = 3000L,
            sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
            sourceDocumentId = "event-1",
            sourceLineId = "event-1",
            sourceOperationId = "reversal:mov-1",
            reversalOfMovementId = "mov-1",
            createdAt = 3000L
        )
        validator.validateVoidedHistory(event, listOf(original, reversal))
    }

    @Test
    fun reversalOfReversal_throws() {
        val event = createBaseEvent().copy(status = DocumentStatus.VOIDED.name, voidedAt = 3000L, updatedAt = 3000L)
        val original = createBaseMovement().copy(movementType = InventoryMovementType.REVERSAL.name, reversalOfMovementId = "prev")
        val reversal = createBaseMovement().copy(movementType = InventoryMovementType.REVERSAL.name, reversalOfMovementId = original.id)
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validateVoidedHistory(event, listOf(original, reversal))
        }
    }

    @Test
    fun zeroWasteQuantity_throws() {
        val movement = createBaseMovement().copy(quantityBaseSigned = "0.0")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun wrongIngredient_throws() {
        val movement = createBaseMovement().copy(ingredientId = "other")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun wrongArea_throws() {
        val movement = createBaseMovement().copy(areaId = "other")
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validatePostedHistory(createBaseEvent(), listOf(movement))
        }
    }

    @Test
    fun missingReversal_throws() {
        val event = createBaseEvent().copy(status = DocumentStatus.VOIDED.name, voidedAt = 3000L, updatedAt = 3000L)
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validateVoidedHistory(event, listOf(createBaseMovement()))
        }
    }

    @Test
    fun wrongReversalTarget_throws() {
        val event = createBaseEvent().copy(status = DocumentStatus.VOIDED.name, voidedAt = 3000L, updatedAt = 3000L)
        val original = createBaseMovement()
        val reversal = InventoryMovementEntity(
            id = "rev-1",
            restaurantId = "rest-1",
            ingredientId = "ing-1",
            areaId = "area-1",
            movementType = InventoryMovementType.REVERSAL.name,
            quantityBaseSigned = "10.0",
            unitCostBaseSnapshot = "2.0",
            totalValueSnapshot = "20.0",
            effectiveAt = 3000L,
            sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
            sourceDocumentId = "event-1",
            sourceLineId = "event-1",
            sourceOperationId = "reversal:wrong",
            reversalOfMovementId = "wrong",
            createdAt = 3000L
        )
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validateVoidedHistory(event, listOf(original, reversal))
        }
    }

    @Test
    fun wrongReversalOperationId_throws() {
        val event = createBaseEvent().copy(status = DocumentStatus.VOIDED.name, voidedAt = 3000L, updatedAt = 3000L)
        val original = createBaseMovement()
        val reversal = InventoryMovementEntity(
            id = "rev-1",
            restaurantId = "rest-1",
            ingredientId = "ing-1",
            areaId = "area-1",
            movementType = InventoryMovementType.REVERSAL.name,
            quantityBaseSigned = "10.0",
            unitCostBaseSnapshot = "2.0",
            totalValueSnapshot = "20.0",
            effectiveAt = 3000L,
            sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
            sourceDocumentId = "event-1",
            sourceLineId = "event-1",
            sourceOperationId = "wrong-op",
            reversalOfMovementId = "mov-1",
            createdAt = 3000L
        )
        assertThrows(ValidationError.MalformedWasteMovementHistory::class.java) {
            validator.validateVoidedHistory(event, listOf(original, reversal))
        }
    }
}
