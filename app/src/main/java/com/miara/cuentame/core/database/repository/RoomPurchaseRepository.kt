package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPurchaseRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val purchaseDao: PurchaseDao,
    private val supplierDao: SupplierDao,
    private val ingredientDao: IngredientDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val areaDao: InventoryAreaDao,
    private val movementDao: InventoryMovementDao,
    private val restaurantDao: RestaurantDao,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : PurchaseRepository {

    override fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>> {
        return purchaseDao.observeFilteredReceipts(
            restaurantId = filter.restaurantId.value,
            status = filter.status?.name,
            supplierId = filter.supplierId?.value,
            query = filter.query?.trim()?.ifBlank { null }
        ).flatMapLatest { receipts ->
            val summaryFlows = receipts.map { entity ->
                val linesFlow = purchaseDao.observeLinesForReceipt(entity.id)
                val supplierFlow = if (entity.supplierId != null) {
                    supplierDao.observeById(entity.supplierId)
                } else {
                    kotlinx.coroutines.flow.flowOf(null)
                }
                
                combine(linesFlow, supplierFlow) { lines, supplier ->
                    PurchaseSummary(
                        receipt = entity.toDomain(),
                        supplierName = supplier?.name,
                        lineCount = lines.size,
                        totalAmount = lines.fold(BigDecimal.ZERO) { acc, line ->
                            acc.add(BigDecimal(line.lineTotal))
                        }
                    )
                }
            }
            if (summaryFlows.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(summaryFlows) { it.toList() }
            }
        }
    }

    override fun observePurchase(id: PurchaseReceiptId): Flow<PurchaseDetails?> {
        return purchaseDao.observeReceiptById(id.value).flatMapLatest { receiptEntity ->
            if (receiptEntity == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            
            val linesFlow = purchaseDao.observeLinesForReceipt(id.value)
            val supplierFlow = if (receiptEntity.supplierId != null) {
                supplierDao.observeById(receiptEntity.supplierId)
            } else {
                kotlinx.coroutines.flow.flowOf(null)
            }

            combine(linesFlow, supplierFlow) { lineEntities, supplierEntity ->
                val linesWithDetails = lineEntities.map { lineEntity ->
                    val ingredient = ingredientDao.getById(lineEntity.ingredientId)
                    val area = areaDao.getById(lineEntity.areaId)
                    val option = unitOptionDao.getById(lineEntity.ingredientUnitOptionId)
                    val baseUnit = ingredient?.let { com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS.find { u -> u.id == it.baseUnitId } }

                    PurchaseLineWithDetails(
                        line = lineEntity.toDomain(),
                        ingredientName = ingredient?.name ?: "Unknown Ingredient",
                        areaName = area?.name ?: "Unknown Area",
                        unitOptionName = option?.displayName ?: "Unknown Unit",
                        baseUnitSymbol = baseUnit?.symbol ?: ""
                    )
                }

                PurchaseDetails(
                    receipt = receiptEntity.toDomain(),
                    supplierName = supplierEntity?.name,
                    lines = linesWithDetails
                )
            }
        }
    }

    override suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt? {
        return purchaseDao.getReceiptById(id.value)?.toDomain()
    }

    override suspend fun createDraft(command: CreatePurchaseDraftCommand): PurchaseReceiptId {
        val restaurant = restaurantDao.getById(command.restaurantId.value)
            ?: throw ValidationError.RecordNotFound
        if (restaurant.deletedAt != null) throw ValidationError.ArchivedReference

        if (command.supplierId != null) {
            val supplier = supplierDao.getById(command.supplierId.value)
                ?: throw ValidationError.SupplierNotFound
            if (supplier.restaurantId != command.restaurantId.value) throw ValidationError.SupplierOwnershipMismatch
            if (!supplier.isActive || supplier.deletedAt != null) throw ValidationError.SupplierArchived
        }

        val now = timeProvider.now()
        val receipt = PurchaseReceipt(
            id = PurchaseReceiptId(idGenerator.newId()),
            restaurantId = command.restaurantId,
            supplierId = command.supplierId,
            invoiceNumber = command.invoiceNumber?.trim()?.ifBlank { null },
            purchaseDate = command.purchaseDate,
            status = DocumentStatus.DRAFT,
            notes = command.notes?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now
        )

        purchaseDao.insertReceipt(receipt.toEntity())
        return receipt.id
    }

    override suspend fun updateDraft(command: UpdatePurchaseDraftCommand) {
        database.withTransaction {
            val existing = purchaseDao.getReceiptById(command.receiptId.value)
                ?: throw ValidationError.PurchaseNotFound
            
            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.PurchaseNotDraft
            }

            if (command.supplierId != null) {
                val supplier = supplierDao.getById(command.supplierId.value)
                    ?: throw ValidationError.SupplierNotFound
                if (supplier.restaurantId != existing.restaurantId) throw ValidationError.SupplierOwnershipMismatch
                if (!supplier.isActive || supplier.deletedAt != null) throw ValidationError.SupplierArchived
            }

            val updated = existing.copy(
                supplierId = command.supplierId?.value,
                invoiceNumber = command.invoiceNumber?.trim()?.ifBlank { null },
                purchaseDate = command.purchaseDate.toEpochMilli(),
                notes = command.notes?.trim()?.ifBlank { null },
                updatedAt = timeProvider.now().toEpochMilli()
            )

            purchaseDao.updateReceipt(updated)
        }
    }

    override suspend fun saveLine(command: SavePurchaseLineCommand): PurchaseLineId {
        return database.withTransaction {
            val receipt = purchaseDao.getReceiptById(command.receiptId.value)
                ?: throw ValidationError.PurchaseNotFound
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val ingredient = ingredientDao.getById(command.ingredientId.value)
                ?: throw ValidationError.IngredientNotFound
            if (ingredient.restaurantId != receipt.restaurantId) throw ValidationError.IngredientOwnershipMismatch
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

            val area = areaDao.getById(command.areaId.value)
                ?: throw ValidationError.RecordNotFound
            if (area.restaurantId != receipt.restaurantId) throw ValidationError.RecordNotFound
            if (!area.isActive || area.deletedAt != null) throw ValidationError.ArchivedReference

            val option = unitOptionDao.getById(command.ingredientUnitOptionId.value)
                ?: throw ValidationError.UnitOptionNotFound
            if (option.ingredientId != command.ingredientId.value) throw ValidationError.InvalidPurchaseUnitOption
            if (!option.isActive || option.deletedAt != null) throw ValidationError.ArchivedReference

            if (command.quantityEntered <= BigDecimal.ZERO) throw ValidationError.InvalidPurchaseQuantity
            if (command.lineTotal < BigDecimal.ZERO) throw ValidationError.InvalidPurchaseLineTotal

            val quantityBase = command.quantityEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
            val unitCostBase = if (quantityBase > BigDecimal.ZERO) {
                command.lineTotal.divide(quantityBase, MathContext.DECIMAL128)
            } else BigDecimal.ZERO

            val now = timeProvider.now()
            val lineId = command.lineId ?: PurchaseLineId(idGenerator.newId())
            
            val line = PurchaseLine(
                id = lineId,
                purchaseReceiptId = command.receiptId,
                ingredientId = command.ingredientId,
                areaId = command.areaId,
                ingredientUnitOptionId = command.ingredientUnitOptionId,
                quantityEntered = command.quantityEntered,
                quantityBase = quantityBase,
                lineTotal = command.lineTotal,
                unitCostBase = unitCostBase,
                notes = command.notes?.trim()?.ifBlank { null },
                createdAt = if (command.lineId == null) now else Instant.ofEpochMilli(purchaseDao.getLineById(lineId.value)?.createdAt ?: now.toEpochMilli()),
                updatedAt = now
            )

            if (command.lineId == null) {
                purchaseDao.insertLine(line.toEntity())
            } else {
                purchaseDao.updateLine(line.toEntity())
            }

            lineId
        }
    }

    override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: PurchaseLineId) {
        database.withTransaction {
            val receipt = purchaseDao.getReceiptById(receiptId.value)
                ?: throw ValidationError.PurchaseNotFound
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val line = purchaseDao.getLineById(lineId.value)
                ?: throw ValidationError.PurchaseLineNotFound
            if (line.purchaseReceiptId != receiptId.value) throw ValidationError.PurchaseLineOwnershipMismatch

            purchaseDao.deleteLine(lineId.value)
        }
    }

    override suspend fun deleteDraft(id: PurchaseReceiptId) {
        database.withTransaction {
            val receipt = purchaseDao.getReceiptById(id.value)
                ?: throw ValidationError.PurchaseNotFound
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft
            
            purchaseDao.deleteDraftWithLines(id.value)
        }
    }

    override suspend fun post(id: PurchaseReceiptId) {
        database.withTransaction {
            val receipt = purchaseDao.getReceiptById(id.value)
                ?: throw ValidationError.PurchaseNotFound
            
            if (receipt.status == DocumentStatus.POSTED.name) {
                // Idempotency check
                validatePostedHistory(receipt)
                return@withTransaction
            }

            if (receipt.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.InvalidPurchaseStatusTransition
            }

            val lines = purchaseDao.getLinesForReceipt(id.value)
            if (lines.isEmpty()) throw ValidationError.PurchaseHasNoLines

            // Re-validate references and re-calculate canonical values
            val movements = lines.map { lineEntity ->
                val ingredient = ingredientDao.getById(lineEntity.ingredientId)
                    ?: throw ValidationError.InvalidPurchaseIngredient
                if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

                val area = areaDao.getById(lineEntity.areaId)
                    ?: throw ValidationError.InvalidPurchaseArea
                if (!area.isActive || area.deletedAt != null) throw ValidationError.ArchivedReference

                val option = unitOptionDao.getById(lineEntity.ingredientUnitOptionId)
                    ?: throw ValidationError.InvalidPurchaseUnitOption
                if (!option.isActive || option.deletedAt != null) throw ValidationError.ArchivedReference

                // Final authoritative calculation
                val qtyBase = BigDecimal(lineEntity.quantityEntered).multiply(option.factorToBase, MathContext.DECIMAL128)
                val costBase = if (qtyBase > BigDecimal.ZERO) {
                    BigDecimal(lineEntity.lineTotal).divide(qtyBase, MathContext.DECIMAL128)
                } else BigDecimal.ZERO

                // Update line with canonical calculations just in case
                purchaseDao.updateLine(lineEntity.copy(
                    quantityBase = qtyBase.toPlainString(),
                    unitCostBase = costBase.toPlainString(),
                    updatedAt = timeProvider.now().toEpochMilli()
                ))

                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = receipt.restaurantId,
                    ingredientId = lineEntity.ingredientId,
                    areaId = lineEntity.areaId,
                    movementType = InventoryMovementType.PURCHASE.name,
                    quantityBaseSigned = qtyBase.toPlainString(),
                    unitCostBaseSnapshot = costBase.toPlainString(),
                    totalValueSnapshot = lineEntity.lineTotal,
                    effectiveAt = receipt.purchaseDate,
                    sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                    sourceDocumentId = receipt.id,
                    sourceOperationId = "purchase-post:${receipt.id}:${lineEntity.id}",
                    sourceLineId = lineEntity.id,
                    reversalOfMovementId = null,
                    createdAt = timeProvider.now().toEpochMilli()
                )
            }

            // Check for existing movements (malformed history for DRAFT)
            val existingMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)
            if (existingMovements.isNotEmpty()) throw ValidationError.MalformedPurchaseMovementHistory

            // Insert movements
            movementDao.insertAll(movements)

            // Update projections
            val affectedIngredients = lines.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(com.miara.cuentame.core.common.ids.IngredientId(ingredientId))
            }

            // Update receipt status
            val now = timeProvider.now().toEpochMilli()
            purchaseDao.updateReceipt(receipt.copy(
                status = DocumentStatus.POSTED.name,
                postedAt = now,
                updatedAt = now
            ))
        }
    }

    override suspend fun void(id: PurchaseReceiptId) {
        database.withTransaction {
            val receipt = purchaseDao.getReceiptById(id.value)
                ?: throw ValidationError.PurchaseNotFound
            
            if (receipt.status == DocumentStatus.VOIDED.name) {
                // Idempotency check
                validateVoidedHistory(receipt)
                return@withTransaction
            }

            if (receipt.status != DocumentStatus.POSTED.name) {
                throw ValidationError.InvalidPurchaseStatusTransition
            }

            // Load original movements
            val originalMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)
                .filter { it.movementType == InventoryMovementType.PURCHASE.name }
            
            val lines = purchaseDao.getLinesForReceipt(id.value)
            if (originalMovements.size != lines.size) throw ValidationError.MalformedPurchaseMovementHistory

            // Create reversals
            val now = timeProvider.now().toEpochMilli()
            val reversals = originalMovements.map { original ->
                if (movementDao.findReversalFor(original.id) != null) throw ValidationError.PurchaseReversalAlreadyExists

                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = original.restaurantId,
                    ingredientId = original.ingredientId,
                    areaId = original.areaId,
                    movementType = InventoryMovementType.REVERSAL.name,
                    quantityBaseSigned = BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
                    unitCostBaseSnapshot = original.unitCostBaseSnapshot,
                    totalValueSnapshot = original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
                    effectiveAt = now,
                    sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                    sourceDocumentId = receipt.id,
                    sourceOperationId = "reversal:${original.id}",
                    sourceLineId = original.sourceLineId,
                    reversalOfMovementId = original.id,
                    createdAt = now
                )
            }

            movementDao.insertAll(reversals)

            // Rebuild projections
            val affectedIngredients = lines.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(com.miara.cuentame.core.common.ids.IngredientId(ingredientId))
            }

            // Update receipt status
            purchaseDao.updateReceipt(receipt.copy(
                status = DocumentStatus.VOIDED.name,
                voidedAt = now,
                updatedAt = now
            ))
        }
    }

    private suspend fun validatePostedHistory(receipt: com.miara.cuentame.core.database.entity.PurchaseReceiptEntity) {
        val lines = purchaseDao.getLinesForReceipt(receipt.id)
        val movements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)
        
        if (movements.size != lines.size) throw ValidationError.MalformedPurchaseMovementHistory
        
        val lineIds = lines.map { it.id }.toSet()
        movements.forEach { mov ->
            if (mov.movementType != InventoryMovementType.PURCHASE.name) throw ValidationError.MalformedPurchaseMovementHistory
            if (mov.sourceLineId !in lineIds) throw ValidationError.MalformedPurchaseMovementHistory
            // Additional field checks could go here
        }
    }

    private suspend fun validateVoidedHistory(receipt: com.miara.cuentame.core.database.entity.PurchaseReceiptEntity) {
        val lines = purchaseDao.getLinesForReceipt(receipt.id)
        val allMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)
        
        val purchases = allMovements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
        val reversals = allMovements.filter { it.movementType == InventoryMovementType.REVERSAL.name }

        if (purchases.size != lines.size) throw ValidationError.MalformedPurchaseMovementHistory
        if (reversals.size != purchases.size) throw ValidationError.MalformedPurchaseMovementHistory

        val purchaseMap = purchases.associateBy { it.id }
        reversals.forEach { rev ->
            val original = purchaseMap[rev.reversalOfMovementId] ?: throw ValidationError.MalformedPurchaseMovementHistory
            if (BigDecimal(rev.quantityBaseSigned) != BigDecimal(original.quantityBaseSigned).negate()) throw ValidationError.MalformedPurchaseMovementHistory
        }
    }
}
