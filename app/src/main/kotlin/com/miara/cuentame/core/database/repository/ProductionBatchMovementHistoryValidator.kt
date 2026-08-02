package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.ProductionBatchComponentEntity
import com.miara.cuentame.core.database.entity.ProductionBatchEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import javax.inject.Inject

class ProductionBatchMovementHistoryValidator @Inject constructor() {

    fun validateDraftHistory(
        batch: ProductionBatchEntity,
        movements: List<InventoryMovementEntity>
    ) {
        if (batch.postedAt != null || batch.voidedAt != null) {
            throw ValidationError.MalformedProductionMovementHistory
        }
        if (movements.isNotEmpty()) {
            throw ValidationError.MalformedProductionMovementHistory
        }
    }

    fun validatePostedHistory(
        batch: ProductionBatchEntity,
        components: List<ProductionBatchComponentEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (batch.postedAt == null || batch.voidedAt != null) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        val consumptionMoves = movements.filter { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }
        val outputMoves = movements.filter { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }
        
        if (consumptionMoves.size != components.size) throw ValidationError.MalformedProductionMovementHistory
        if (outputMoves.size != 1) throw ValidationError.MalformedProductionMovementHistory
        if (movements.size != (consumptionMoves.size + outputMoves.size)) throw ValidationError.MalformedProductionMovementHistory

        val consumptionLineIds = consumptionMoves.mapNotNull { it.sourceLineId }
        if (consumptionLineIds.size != consumptionMoves.size) throw ValidationError.MalformedProductionMovementHistory
        if (consumptionLineIds.distinct().size != consumptionLineIds.size) throw ValidationError.MalformedProductionMovementHistory
        
        val componentIds = components.map { it.id }.toSet()
        if (consumptionLineIds.toSet() != componentIds) throw ValidationError.MalformedProductionMovementHistory

        val consumptionByLineId = consumptionMoves.associateBy { it.sourceLineId }
        components.forEach { component ->
            val movement = consumptionByLineId[component.id]!!
            validateConsumptionMovementMatchesComponent(batch, component, movement)
        }

        val outputMove = outputMoves.first()
        if (outputMove.sourceLineId != batch.id) throw ValidationError.MalformedProductionMovementHistory
        validateOutputMovementMatchesBatch(batch, outputMove)

        // Ensure no reversals exist in POSTED state
        if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
            throw ValidationError.MalformedProductionMovementHistory
        }
    }

    fun validateVoidedHistory(
        batch: ProductionBatchEntity,
        components: List<ProductionBatchComponentEntity>,
        movements: List<InventoryMovementEntity>
    ) {
        if (batch.postedAt == null || batch.voidedAt == null) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        val originalMoves = movements.filter {
            it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name ||
            it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name
        }
        val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        if (originalMoves.size != (components.size + 1)) throw ValidationError.MalformedProductionMovementHistory
        if (reversals.size != originalMoves.size) throw ValidationError.MalformedProductionMovementHistory
        if (movements.size != (originalMoves.size + reversals.size)) throw ValidationError.MalformedProductionMovementHistory

        val reversalTargetIds = reversals.mapNotNull { it.reversalOfMovementId }
        if (reversalTargetIds.size != reversals.size) throw ValidationError.MalformedProductionMovementHistory
        if (reversalTargetIds.distinct().size != reversalTargetIds.size) throw ValidationError.MalformedProductionMovementHistory
        
        val originalMoveIds = originalMoves.map { it.id }.toSet()
        if (reversalTargetIds.toSet() != originalMoveIds) throw ValidationError.MalformedProductionMovementHistory

        val reversalsByOriginalId = reversals.associateBy { it.reversalOfMovementId }

        originalMoves.forEach { original ->
            val reversal = reversalsByOriginalId[original.id]!!
            validateReversalMatchesOriginal(batch, original, reversal)
        }

        val consumptionByLineId = originalMoves.filter { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }
            .associateBy { it.sourceLineId }
        components.forEach { component ->
            val movement = consumptionByLineId[component.id] ?: throw ValidationError.MalformedProductionMovementHistory
            validateConsumptionMovementMatchesComponent(batch, component, movement)
        }

        val outputMove = originalMoves.find { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }
            ?: throw ValidationError.MalformedProductionMovementHistory
        validateOutputMovementMatchesBatch(batch, outputMove)
    }

    private fun validateConsumptionMovementMatchesComponent(
        batch: ProductionBatchEntity,
        component: ProductionBatchComponentEntity,
        movement: InventoryMovementEntity
    ) {
        if (movement.movementType != InventoryMovementType.PRODUCTION_CONSUMPTION.name) throw ValidationError.MalformedProductionMovementHistory
        if (movement.restaurantId != batch.restaurantId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.ingredientId != component.componentIngredientId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.areaId != component.sourceAreaId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceDocumentId != batch.id) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceOperationId != "production-post:${batch.id}:consume:${component.id}") throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceLineId != component.id) throw ValidationError.MalformedProductionMovementHistory
        if (movement.reversalOfMovementId != null) throw ValidationError.MalformedProductionMovementHistory

        val qty = BigDecimal(movement.quantityBaseSigned)
        if (qty >= BigDecimal.ZERO || qty.compareTo(BigDecimal(component.actualQuantityBase).negate()) != 0) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (!isNumericallyEquivalent(movement.unitCostBaseSnapshot, component.unitCostBaseSnapshot)) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (!isNumericallyEquivalent(movement.totalValueSnapshot, component.totalCostSnapshot?.let { BigDecimal(it).negate().toPlainString() })) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (movement.effectiveAt != batch.effectiveAt) throw ValidationError.MalformedProductionMovementHistory
    }

    private fun validateOutputMovementMatchesBatch(
        batch: ProductionBatchEntity,
        movement: InventoryMovementEntity
    ) {
        if (movement.movementType != InventoryMovementType.PRODUCTION_OUTPUT.name) throw ValidationError.MalformedProductionMovementHistory
        if (movement.restaurantId != batch.restaurantId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.ingredientId != batch.outputIngredientId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.areaId != batch.outputAreaId) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceDocumentId != batch.id) throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceOperationId != "production-post:${batch.id}:output") throw ValidationError.MalformedProductionMovementHistory
        if (movement.sourceLineId != batch.id) throw ValidationError.MalformedProductionMovementHistory
        if (movement.reversalOfMovementId != null) throw ValidationError.MalformedProductionMovementHistory

        val qty = BigDecimal(movement.quantityBaseSigned)
        if (qty <= BigDecimal.ZERO || qty.compareTo(BigDecimal(batch.actualOutputQuantityBase)) != 0) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (!isNumericallyEquivalent(movement.unitCostBaseSnapshot, batch.outputUnitCostBaseSnapshot)) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (!isNumericallyEquivalent(movement.totalValueSnapshot, batch.totalComponentCostSnapshot)) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (movement.effectiveAt != batch.effectiveAt) throw ValidationError.MalformedProductionMovementHistory
    }

    private fun validateReversalMatchesOriginal(
        batch: ProductionBatchEntity,
        original: InventoryMovementEntity,
        reversal: InventoryMovementEntity
    ) {
        if (reversal.movementType != InventoryMovementType.REVERSAL.name) throw ValidationError.MalformedProductionMovementHistory
        if (original.reversalOfMovementId != null) throw ValidationError.MalformedProductionMovementHistory

        if (reversal.restaurantId != original.restaurantId) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.ingredientId != original.ingredientId) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.areaId != original.areaId) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.sourceDocumentId != batch.id) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.sourceOperationId != "reversal:${original.id}") throw ValidationError.MalformedProductionMovementHistory
        if (reversal.sourceLineId != original.sourceLineId) throw ValidationError.MalformedProductionMovementHistory
        if (reversal.reversalOfMovementId != original.id) throw ValidationError.MalformedProductionMovementHistory

        if (BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        if (!isNumericallyEquivalent(reversal.unitCostBaseSnapshot, original.unitCostBaseSnapshot)) {
            throw ValidationError.MalformedProductionMovementHistory
        }

        val originalTotal = original.totalValueSnapshot?.let { BigDecimal(it) }
        val reversalTotal = reversal.totalValueSnapshot?.let { BigDecimal(it) }

        if (originalTotal == null && reversalTotal != null) throw ValidationError.MalformedProductionMovementHistory
        if (originalTotal != null && reversalTotal == null) throw ValidationError.MalformedProductionMovementHistory
        if (originalTotal != null && reversalTotal != null) {
            if (reversalTotal.compareTo(originalTotal.negate()) != 0) {
                throw ValidationError.MalformedProductionMovementHistory
            }
        }

        if (reversal.effectiveAt != batch.voidedAt) throw ValidationError.MalformedProductionMovementHistory
        // Reversal createdAt should be when the void happened
        if (reversal.createdAt != batch.voidedAt) throw ValidationError.MalformedProductionMovementHistory
    }

    private fun isNumericallyEquivalent(a: String?, b: String?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return try {
            BigDecimal(a).compareTo(BigDecimal(b)) == 0
        } catch (_: Exception) {
            false
        }
    }
}
