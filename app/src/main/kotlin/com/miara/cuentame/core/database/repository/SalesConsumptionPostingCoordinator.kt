package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.salesimport.SalesTransactionSourceIdentity
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

enum class SalesConsumptionFailureCode {
    TRANSACTION_NOT_FOUND, PUBLICATION_NOT_FOUND, PUBLICATION_MISMATCH,
    PUBLICATION_ITEM_NOT_FOUND, CONSUMPTION_REVISION_MISMATCH,
    INVALID_COMPONENT_SNAPSHOT, INGREDIENT_NOT_FOUND, AREA_NOT_FOUND,
    OWNERSHIP_MISMATCH, HISTORY_CONFLICT, PERSISTENCE_FAILURE
}

sealed interface SalesConsumptionTransactionResult {
    data object Applied : SalesConsumptionTransactionResult
    data object Reversed : SalesConsumptionTransactionResult
    data object AlreadyAligned : SalesConsumptionTransactionResult
    data object NoEffect : SalesConsumptionTransactionResult
    data class Failed(val code: SalesConsumptionFailureCode) : SalesConsumptionTransactionResult
}

data class SalesConsumptionImportResult(
    val results: Map<Pair<String, String>, SalesConsumptionTransactionResult>
)

enum class SalesConsumptionAlignment {
    ALIGNED, NO_EFFECT, NEEDS_RECONCILIATION, HISTORY_CONFLICT, INVALID_SOURCE
}

data class SalesConsumptionImportAlignment(
    val alignments: Map<Pair<String, String>, SalesConsumptionAlignment>
)

private data class ExpectedSalesConsumption(
    val restaurantId: String,
    val ingredientId: String,
    val areaId: String,
    val quantityBaseSigned: BigDecimal,
    val effectiveAt: Long,
    val sourceDocumentId: String,
    val sourceOperationId: String,
    val sourceLineId: String
)

private class SalesConsumptionException(val code: SalesConsumptionFailureCode) : RuntimeException()

@Singleton
class SalesConsumptionPostingCoordinator @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val salesDao: SalesImportDao,
    private val publicationDao: MenuPublicationDao,
    private val ingredientDao: IngredientDao,
    private val areaDao: InventoryAreaDao,
    private val movementDao: InventoryMovementDao,
    private val snapshotService: InventorySnapshotService,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) {
    suspend fun reconcileImport(exportId: String): SalesConsumptionImportResult {
        try {
            val transactions = salesDao.getTransactionsForImport(exportId)
            return SalesConsumptionImportResult(transactions.associate { transaction ->
                (transaction.terminalId to transaction.transactionId) to
                    reconcileTransaction(transaction.terminalId, transaction.transactionId)
            })
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun inspectImport(exportId: String): SalesConsumptionImportAlignment {
        val transactions = salesDao.getTransactionsForImport(exportId)
        return SalesConsumptionImportAlignment(transactions.associate { transaction ->
            (transaction.terminalId to transaction.transactionId) to
                inspectTransaction(transaction.terminalId, transaction.transactionId)
        })
    }

    suspend fun inspectTransaction(terminalId: String, transactionId: String): SalesConsumptionAlignment =
        try {
            inspect(terminalId, transactionId)
        } catch (e: CancellationException) {
            throw e
        } catch (failure: SalesConsumptionException) {
            when (failure.code) {
                SalesConsumptionFailureCode.HISTORY_CONFLICT -> SalesConsumptionAlignment.HISTORY_CONFLICT
                else -> SalesConsumptionAlignment.INVALID_SOURCE
            }
        } catch (_: Exception) {
            SalesConsumptionAlignment.INVALID_SOURCE
        }

    suspend fun reconcileTransaction(terminalId: String, transactionId: String): SalesConsumptionTransactionResult =
        try {
            database.withTransaction { reconcileAtomic(terminalId, transactionId) }
        } catch (failure: SalesConsumptionException) {
            SalesConsumptionTransactionResult.Failed(failure.code)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SalesConsumptionTransactionResult.Failed(SalesConsumptionFailureCode.PERSISTENCE_FAILURE)
        }

    private suspend fun reconcileAtomic(terminalId: String, transactionId: String): SalesConsumptionTransactionResult {
        val transaction = salesDao.getTransaction(terminalId, transactionId)
            ?: fail(SalesConsumptionFailureCode.TRANSACTION_NOT_FOUND)
        val sourceId = SalesTransactionSourceIdentity.encode(terminalId, transactionId)
        val existing = movementDao.getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name, sourceId)
        val originals = existing.filter { it.movementType == InventoryMovementType.SALES_CONSUMPTION.name }
        val reversals = existing.filter { it.movementType == InventoryMovementType.REVERSAL.name }
        if (existing.size != originals.size + reversals.size) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)

        if (transaction.status == "VOIDED" && originals.isEmpty() && reversals.isEmpty()) {
            return SalesConsumptionTransactionResult.NoEffect
        }

        val expectedSpec = expectedConsumption(transaction, sourceId)
        validateOriginalsAgainstSpec(originals, expectedSpec)
        validateReversals(originals, reversals, sourceId)

        return when (transaction.status) {
            "COMPLETED" -> {
                if (reversals.isNotEmpty()) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
                if (originals.isNotEmpty()) SalesConsumptionTransactionResult.AlreadyAligned
                else if (expectedSpec.isEmpty()) SalesConsumptionTransactionResult.NoEffect
                else {
                    val expected = createMovements(expectedSpec)
                    movementDao.insertAll(expected)
                    rebuild(expected)
                    SalesConsumptionTransactionResult.Applied
                }
            }
            "VOIDED" -> {
                if (originals.isEmpty()) SalesConsumptionTransactionResult.NoEffect
                else if (reversals.size == originals.size) SalesConsumptionTransactionResult.AlreadyAligned
                else if (reversals.isNotEmpty()) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
                else {
                    val now = timeProvider.now().toEpochMilli()
                    val rows = originals.map { original ->
                        InventoryMovementEntity(
                            idGenerator.newId(), original.restaurantId, original.ingredientId, original.areaId,
                            InventoryMovementType.REVERSAL.name,
                            BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
                            original.unitCostBaseSnapshot,
                            original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
                            now, SourceDocumentType.SALES_TRANSACTION.name, sourceId,
                            InventoryMovementOperationIds.reversal(original.id), original.sourceLineId,
                            original.id, now
                        )
                    }
                    movementDao.insertAll(rows)
                    rebuild(rows)
                    SalesConsumptionTransactionResult.Reversed
                }
            }
            else -> fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        }
    }

    private suspend fun inspect(terminalId: String, transactionId: String): SalesConsumptionAlignment {
        val transaction = salesDao.getTransaction(terminalId, transactionId)
            ?: fail(SalesConsumptionFailureCode.TRANSACTION_NOT_FOUND)
        val sourceId = SalesTransactionSourceIdentity.encode(terminalId, transactionId)
        val existing = movementDao.getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name, sourceId)
        val originals = existing.filter { it.movementType == InventoryMovementType.SALES_CONSUMPTION.name }
        val reversals = existing.filter { it.movementType == InventoryMovementType.REVERSAL.name }
        if (existing.size != originals.size + reversals.size) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        val expected = expectedConsumption(transaction, sourceId)
        validateOriginalsAgainstSpec(originals, expected)
        validateReversals(originals, reversals, sourceId)
        return when (transaction.status) {
            "COMPLETED" -> when {
                reversals.isNotEmpty() -> SalesConsumptionAlignment.HISTORY_CONFLICT
                originals.isEmpty() && expected.isEmpty() -> SalesConsumptionAlignment.NO_EFFECT
                originals.isEmpty() -> SalesConsumptionAlignment.NEEDS_RECONCILIATION
                else -> SalesConsumptionAlignment.ALIGNED
            }
            "VOIDED" -> when {
                originals.isEmpty() && reversals.isEmpty() -> SalesConsumptionAlignment.NO_EFFECT
                reversals.isEmpty() -> SalesConsumptionAlignment.NEEDS_RECONCILIATION
                else -> SalesConsumptionAlignment.ALIGNED
            }
            else -> SalesConsumptionAlignment.HISTORY_CONFLICT
        }
    }

    private suspend fun expectedConsumption(transaction: ImportedSaleTransactionEntity, sourceId: String): List<ExpectedSalesConsumption> {
        val publication = publicationDao.getPublication(transaction.menuPackageId)
            ?: fail(SalesConsumptionFailureCode.PUBLICATION_NOT_FOUND)
        if (publication.restaurantId != transaction.restaurantId || publication.sourceMenuId != transaction.menuId ||
            publication.publicationRevision != transaction.publicationRevision) fail(SalesConsumptionFailureCode.PUBLICATION_MISMATCH)
        val items = publicationDao.getItems(publication.id).associateBy { it.menuRecipeId }
        val components = publicationDao.getComponents(publication.id).groupBy { it.publicationItemId }
        val lines = salesDao.getLines(transaction.terminalId, transaction.transactionId)
        val result = mutableListOf<ExpectedSalesConsumption>()
        for (line in lines) {
            val item = items[line.sellableItemId] ?: fail(SalesConsumptionFailureCode.PUBLICATION_ITEM_NOT_FOUND)
            if (line.consumptionRevision != item.consumptionRevision) fail(SalesConsumptionFailureCode.CONSUMPTION_REVISION_MISMATCH)
            for (component in components[item.id].orEmpty()) {
                val quantity = line.quantity.multiply(component.quantityBaseSnapshot)
                if (quantity <= BigDecimal.ZERO || component.inventoryAreaIdSnapshot.isBlank()) fail(SalesConsumptionFailureCode.INVALID_COMPONENT_SNAPSHOT)
                val ingredient = ingredientDao.getById(component.ingredientId) ?: fail(SalesConsumptionFailureCode.INGREDIENT_NOT_FOUND)
                val area = areaDao.getById(component.inventoryAreaIdSnapshot) ?: fail(SalesConsumptionFailureCode.AREA_NOT_FOUND)
                if (ingredient.restaurantId != transaction.restaurantId || area.restaurantId != transaction.restaurantId) fail(SalesConsumptionFailureCode.OWNERSHIP_MISMATCH)
                result += ExpectedSalesConsumption(
                    transaction.restaurantId, component.ingredientId, component.inventoryAreaIdSnapshot,
                    quantity.negate(), transaction.closedAt, sourceId,
                    InventoryMovementOperationIds.salesConsumption(line.saleLineId, component.id), line.saleLineId
                )
            }
        }
        return result
    }

    private suspend fun createMovements(expected: List<ExpectedSalesConsumption>): List<InventoryMovementEntity> {
        val now = timeProvider.now().toEpochMilli()
        return expected.map { spec ->
            val snapshot = snapshotService.calculateAt(RestaurantId(spec.restaurantId), IngredientId(spec.ingredientId), InventoryAreaId(spec.areaId), Instant.ofEpochMilli(spec.effectiveAt))
            val unitCost = snapshot.ingredientAverageCostBase
            InventoryMovementEntity(
                idGenerator.newId(), spec.restaurantId, spec.ingredientId, spec.areaId,
                InventoryMovementType.SALES_CONSUMPTION.name, spec.quantityBaseSigned.toPlainString(),
                unitCost?.toPlainString(), unitCost?.let { spec.quantityBaseSigned.multiply(it).toPlainString() },
                spec.effectiveAt, SourceDocumentType.SALES_TRANSACTION.name, spec.sourceDocumentId,
                spec.sourceOperationId, spec.sourceLineId, null, now
            )
        }
    }

    private fun validateOriginalsAgainstSpec(actual: List<InventoryMovementEntity>, expected: List<ExpectedSalesConsumption>) {
        if (actual.isEmpty()) return
        val expectedByOperation = expected.associateBy { it.sourceOperationId }
        if (actual.size != expected.size || actual.map { it.sourceOperationId }.toSet().size != actual.size) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        actual.forEach { row ->
            val wanted = expectedByOperation[row.sourceOperationId] ?: fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
            if (row.restaurantId != wanted.restaurantId || row.ingredientId != wanted.ingredientId || row.areaId != wanted.areaId ||
                row.sourceDocumentType != SourceDocumentType.SALES_TRANSACTION.name || row.sourceDocumentId != wanted.sourceDocumentId ||
                BigDecimal(row.quantityBaseSigned).compareTo(wanted.quantityBaseSigned) != 0 || row.sourceLineId != wanted.sourceLineId ||
                row.effectiveAt != wanted.effectiveAt || row.reversalOfMovementId != null) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        }
    }

    private fun validateReversals(originals: List<InventoryMovementEntity>, reversals: List<InventoryMovementEntity>, sourceId: String) {
        if (reversals.isNotEmpty() && reversals.size != originals.size) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        if (reversals.mapNotNull { it.reversalOfMovementId }.toSet().size != reversals.size) fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        val byId = originals.associateBy { it.id }
        reversals.forEach { reverse ->
            val original = byId[reverse.reversalOfMovementId] ?: fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
            if (reverse.movementType != InventoryMovementType.REVERSAL.name ||
                reverse.sourceDocumentType != SourceDocumentType.SALES_TRANSACTION.name || reverse.restaurantId != original.restaurantId ||
                reverse.sourceDocumentId != sourceId || reverse.sourceDocumentId != original.sourceDocumentId || reverse.reversalOfMovementId != original.id ||
                reverse.effectiveAt < original.effectiveAt || reverse.createdAt < original.createdAt ||
                reverse.sourceOperationId != InventoryMovementOperationIds.reversal(original.id) ||
                reverse.ingredientId != original.ingredientId || reverse.areaId != original.areaId || reverse.sourceLineId != original.sourceLineId ||
                BigDecimal(reverse.quantityBaseSigned).compareTo(BigDecimal(original.quantityBaseSigned).negate()) != 0 ||
                !sameDecimal(reverse.unitCostBaseSnapshot, original.unitCostBaseSnapshot) ||
                !sameDecimal(reverse.totalValueSnapshot, original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() }))
                fail(SalesConsumptionFailureCode.HISTORY_CONFLICT)
        }
    }

    private fun sameDecimal(left: String?, right: String?): Boolean = when {
        left == null || right == null -> left == right
        else -> runCatching { BigDecimal(left).compareTo(BigDecimal(right)) == 0 }.getOrDefault(false)
    }

    private suspend fun rebuild(rows: List<InventoryMovementEntity>) = rows.map { it.ingredientId }.distinct().forEach {
        projectionRebuilder.rebuildForIngredient(IngredientId(it))
    }

    private fun fail(code: SalesConsumptionFailureCode): Nothing = throw SalesConsumptionException(code)
}
