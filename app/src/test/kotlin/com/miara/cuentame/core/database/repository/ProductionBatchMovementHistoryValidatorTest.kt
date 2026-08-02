package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.ProductionBatchComponentEntity
import com.miara.cuentame.core.database.entity.ProductionBatchEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ProductionBatchMovementHistoryValidatorTest {

    private val validator = ProductionBatchMovementHistoryValidator()

    private val batchId = "pb1"
    private val restId = "r1"
    private val recipeId = "rec1"
    private val outputIngId = "i-out"
    private val compIngId = "i-comp"
    private val areaId = "a1"

    @Test
    fun malformedDraft_withMovements_throwsConflict() {
        val batch = createDraftBatch()
        val movements = listOf(createMovement("m1", InventoryMovementType.PRODUCTION_OUTPUT))
        
        assertThrows(ValidationError.MalformedProductionMovementHistory::class.java) {
            validator.validateDraftHistory(batch, movements)
        }
    }

    @Test
    fun malformedPosted_missingMovement_throwsConflict() {
        val batch = createPostedBatch()
        val components = listOf(createComponent("pbc1"))
        // Missing consumption movement
        val movements = listOf(createMovement("m1", InventoryMovementType.PRODUCTION_OUTPUT, sourceLineId = batchId))
        
        assertThrows(ValidationError.MalformedProductionMovementHistory::class.java) {
            validator.validatePostedHistory(batch, components, movements)
        }
    }

    @Test
    fun malformedPosted_corruptedMovement_throwsConflict() {
        val batch = createPostedBatch()
        val components = listOf(createComponent("pbc1"))
        val movements = listOf(
            createMovement("m1", InventoryMovementType.PRODUCTION_CONSUMPTION, sourceLineId = "pbc1", quantity = "-5"),
            createMovement("m2", InventoryMovementType.PRODUCTION_OUTPUT, sourceLineId = batchId, quantity = "99") // Wrong (batch has 10)
        )
        
        assertThrows(ValidationError.MalformedProductionMovementHistory::class.java) {
            validator.validatePostedHistory(batch, components, movements)
        }
    }

    @Test
    fun malformedVoided_missingReversal_throwsConflict() {
        val batch = createVoidedBatch()
        val components = listOf(createComponent("pbc1"))
        val m1 = createMovement("m1", InventoryMovementType.PRODUCTION_CONSUMPTION, sourceLineId = "pbc1")
        val m2 = createMovement("m2", InventoryMovementType.PRODUCTION_OUTPUT, sourceLineId = batchId)
        
        // Missing one reversal
        val movements = listOf(m1, m2, createReversal("rev1", m1))
        
        assertThrows(ValidationError.MalformedProductionMovementHistory::class.java) {
            validator.validateVoidedHistory(batch, components, movements)
        }
    }

    @Test
    fun malformedVoided_wrongReversalQuantity_throwsConflict() {
        val batch = createVoidedBatch()
        val components = listOf(createComponent("pbc1"))
        val m1 = createMovement("m1", InventoryMovementType.PRODUCTION_CONSUMPTION, sourceLineId = "pbc1")
        val m2 = createMovement("m2", InventoryMovementType.PRODUCTION_OUTPUT, sourceLineId = batchId)
        
        val movements = listOf(
            m1, m2,
            createReversal("rev1", m1),
            createReversal("rev2", m2, quantity = "-99") // Wrong (m2 has 10)
        )
        
        assertThrows(ValidationError.MalformedProductionMovementHistory::class.java) {
            validator.validateVoidedHistory(batch, components, movements)
        }
    }

    private fun createDraftBatch() = ProductionBatchEntity(
        id = batchId, restaurantId = restId, recipeId = recipeId, recipeNameSnapshot = "Rec",
        outputIngredientId = outputIngId, batchMultiplier = "1", recipeStandardYieldQuantitySnapshot = "10",
        recipeStandardYieldBaseSnapshot = "10", recipeYieldUnitOptionIdSnapshot = "o1",
        expectedOutputQuantityEntered = "10", expectedOutputQuantityBase = "10",
        actualOutputQuantityEntered = "10", actualOutputQuantityBase = "10",
        outputUnitOptionId = "o1", outputAreaId = areaId, hasManualOutputQuantityOverride = false,
        totalComponentCostSnapshot = null, outputUnitCostBaseSnapshot = null,
        effectiveAt = 1000, status = "DRAFT", notes = null, createdAt = 1000, updatedAt = 1000,
        postedAt = null, voidedAt = null
    )

    private fun createPostedBatch() = createDraftBatch().copy(
        status = "POSTED", postedAt = 2000, updatedAt = 2000,
        totalComponentCostSnapshot = "50", outputUnitCostBaseSnapshot = "5"
    )

    private fun createVoidedBatch() = createPostedBatch().copy(
        status = "VOIDED", voidedAt = 3000, updatedAt = 3000
    )

    private fun createComponent(id: String) = ProductionBatchComponentEntity(
        id = id, productionBatchId = batchId, sourceRecipeComponentIdSnapshot = "rc1",
        componentIngredientId = compIngId, recipeQuantityEnteredSnapshot = "5",
        recipeQuantityBaseSnapshot = "5", recipeUnitOptionIdSnapshot = "o2",
        expectedQuantityEntered = "5", expectedQuantityBase = "5",
        actualQuantityEntered = "5", actualQuantityBase = "5",
        unitOptionId = "o2", hasManualQuantityOverride = false,
        sourceAreaId = areaId, unitCostBaseSnapshot = "10", totalCostSnapshot = "50",
        sortOrder = 0, notes = null, createdAt = 1000, updatedAt = 1000
    )

    private fun createMovement(
        id: String, type: InventoryMovementType, sourceLineId: String? = null,
        quantity: String? = null, unitCost: String? = null, totalValue: String? = null
    ) = InventoryMovementEntity(
        id = id, restaurantId = restId, ingredientId = if (type == InventoryMovementType.PRODUCTION_OUTPUT) outputIngId else compIngId,
        areaId = areaId, movementType = type.name,
        quantityBaseSigned = quantity ?: (if (type == InventoryMovementType.PRODUCTION_CONSUMPTION) "-5" else "10"),
        unitCostBaseSnapshot = unitCost ?: (if (type == InventoryMovementType.PRODUCTION_CONSUMPTION) "10" else "5"),
        totalValueSnapshot = totalValue ?: (if (type == InventoryMovementType.PRODUCTION_CONSUMPTION) "-50" else "50"),
        effectiveAt = 2000, sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
        sourceDocumentId = batchId, sourceOperationId = "op-$id", sourceLineId = sourceLineId,
        reversalOfMovementId = null, createdAt = 2000
    ).let { move ->
        if (type == InventoryMovementType.PRODUCTION_CONSUMPTION) {
            move.copy(sourceOperationId = "production-post:$batchId:consume:${move.sourceLineId}")
        } else if (type == InventoryMovementType.PRODUCTION_OUTPUT) {
            move.copy(sourceOperationId = "production-post:$batchId:output")
        } else move
    }

    private fun createReversal(id: String, target: InventoryMovementEntity, quantity: String? = null) = InventoryMovementEntity(
        id = id, restaurantId = restId, ingredientId = target.ingredientId, areaId = target.areaId,
        movementType = InventoryMovementType.REVERSAL.name,
        quantityBaseSigned = quantity ?: BigDecimal(target.quantityBaseSigned).negate().toPlainString(),
        unitCostBaseSnapshot = target.unitCostBaseSnapshot,
        totalValueSnapshot = target.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
        effectiveAt = 3000, sourceDocumentType = SourceDocumentType.PRODUCTION_BATCH.name,
        sourceDocumentId = batchId, sourceOperationId = "reversal:${target.id}",
        sourceLineId = target.sourceLineId, reversalOfMovementId = target.id, createdAt = 3000
    )
}
