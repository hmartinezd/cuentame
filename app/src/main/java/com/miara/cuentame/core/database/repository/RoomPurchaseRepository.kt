package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
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
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
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
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
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
    private val referenceValidator: PurchaseReferenceValidator,
    private val lineCalculator: PurchaseLineCalculator,
    private val historyValidator: PurchaseMovementHistoryValidator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : PurchaseRepository {

    private suspend fun requireActiveRestaurant(): RestaurantEntity {
        return restaurantDao.getRestaurant() ?: throw ValidationError.RecordNotFound
    }

    override fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>> {
        return purchaseDao.observeFilteredReceipts(
            restaurantId = filter.restaurantId.value,
            status = filter.status?.name,
            supplierId = filter.supplierId?.value,
            query = filter.query
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
                        ingredientName = ingredient?.name,
                        areaName = area?.name,
                        unitOptionName = option?.displayName,
                        baseUnitSymbol = baseUnit?.symbol
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
        val activeRestaurant = requireActiveRestaurant()
        if (activeRestaurant.id != command.restaurantId.value) throw ValidationError.PurchaseOwnershipMismatch

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
            val activeRestaurant = requireActiveRestaurant()
            val refs = referenceValidator.validateReceiptAndRestaurant(command.receiptId, activeRestaurant)
            val existing = refs.receipt
            
            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.PurchaseNotDraft
            }

            if (command.supplierId != null) {
                val supplier = supplierDao.getById(command.supplierId.value)
                    ?: throw ValidationError.SupplierNotFound
                if (supplier.restaurantId != activeRestaurant.id) throw ValidationError.SupplierOwnershipMismatch
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
            val activeRestaurant = requireActiveRestaurant()
            
            val lineRefs = referenceValidator.validateLineReferences(
                activeRestaurant.id,
                command.ingredientId,
                command.areaId,
                command.ingredientUnitOptionId
            )

            val refs = referenceValidator.validateReceiptAndRestaurant(command.receiptId, activeRestaurant)
            val receipt = refs.receipt
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val calculation = lineCalculator.calculate(
                quantityEntered = command.quantityEntered,
                lineTotal = command.lineTotal,
                optionFactorToBase = lineRefs.unitOption.factorToBase
            )

            val now = timeProvider.now()
            
            if (command.lineId == null) {
                val newLineId = PurchaseLineId(idGenerator.newId())
                val line = PurchaseLine(
                    id = newLineId,
                    purchaseReceiptId = command.receiptId,
                    ingredientId = command.ingredientId,
                    areaId = command.areaId,
                    ingredientUnitOptionId = command.ingredientUnitOptionId,
                    quantityEntered = command.quantityEntered,
                    quantityBase = calculation.quantityBase,
                    lineTotal = command.lineTotal,
                    unitCostBase = calculation.unitCostBase,
                    notes = command.notes?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now
                )
                purchaseDao.insertLine(line.toEntity())
                newLineId
            } else {
                val existingLine = purchaseDao.getLineById(command.lineId.value)
                    ?: throw ValidationError.PurchaseLineNotFound
                
                if (existingLine.purchaseReceiptId != command.receiptId.value) {
                    throw ValidationError.PurchaseLineOwnershipMismatch
                }

                val updatedLine = existingLine.copy(
                    ingredientId = command.ingredientId.value,
                    areaId = command.areaId.value,
                    ingredientUnitOptionId = command.ingredientUnitOptionId.value,
                    quantityEntered = command.quantityEntered.toPlainString(),
                    quantityBase = calculation.quantityBase.toPlainString(),
                    lineTotal = command.lineTotal.toPlainString(),
                    unitCostBase = calculation.unitCostBase.toPlainString(),
                    notes = command.notes?.trim()?.ifBlank { null },
                    updatedAt = now.toEpochMilli()
                )
                
                val affected = purchaseDao.updateLine(updatedLine)
                if (affected != 1) throw ValidationError.PurchaseLineNotFound
                command.lineId
            }
        }
    }

    override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: PurchaseLineId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val refs = referenceValidator.validateReceiptAndRestaurant(receiptId, activeRestaurant)
            val receipt = refs.receipt
            
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val line = purchaseDao.getLineById(lineId.value)
                ?: throw ValidationError.PurchaseLineNotFound
            if (line.purchaseReceiptId != receiptId.value) throw ValidationError.PurchaseLineOwnershipMismatch

            purchaseDao.deleteLine(lineId.value)
        }
    }

    override suspend fun deleteDraft(id: PurchaseReceiptId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val refs = referenceValidator.validateReceiptAndRestaurant(id, activeRestaurant)
            val receipt = refs.receipt
            
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft
            
            purchaseDao.deleteDraftWithLines(id.value)
        }
    }

    override suspend fun post(id: PurchaseReceiptId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val refs = referenceValidator.validateReceiptAndRestaurant(id, activeRestaurant)
            val receipt = refs.receipt

            val lines = purchaseDao.getLinesForReceipt(id.value)
            val existingMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)

            if (receipt.status == DocumentStatus.POSTED.name) {
                historyValidator.validatePostedHistory(receipt, lines, existingMovements)
                return@withTransaction
            }

            if (receipt.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.InvalidPurchaseStatusTransition
            }

            historyValidator.validateDraftHistory(receipt, existingMovements)
            if (lines.isEmpty()) throw ValidationError.PurchaseHasNoLines

            val movements = lines.map { lineEntity ->
                val lineRefs = referenceValidator.validateLineReferences(
                    activeRestaurant.id,
                    IngredientId(lineEntity.ingredientId),
                    InventoryAreaId(lineEntity.areaId),
                    IngredientUnitOptionId(lineEntity.ingredientUnitOptionId)
                )

                val calculation = lineCalculator.calculate(
                    quantityEntered = BigDecimal(lineEntity.quantityEntered),
                    lineTotal = BigDecimal(lineEntity.lineTotal),
                    optionFactorToBase = lineRefs.unitOption.factorToBase
                )

                // Update line with canonical calculations
                purchaseDao.updateLine(lineEntity.copy(
                    quantityBase = calculation.quantityBase.toPlainString(),
                    unitCostBase = calculation.unitCostBase.toPlainString(),
                    updatedAt = timeProvider.now().toEpochMilli()
                ))

                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = activeRestaurant.id,
                    ingredientId = lineEntity.ingredientId,
                    areaId = lineEntity.areaId,
                    movementType = InventoryMovementType.PURCHASE.name,
                    quantityBaseSigned = calculation.quantityBase.toPlainString(),
                    unitCostBaseSnapshot = calculation.unitCostBase.toPlainString(),
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

            movementDao.insertAll(movements)

            val affectedIngredients = lines.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

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
            val activeRestaurant = requireActiveRestaurant()
            val refs = referenceValidator.validateReceiptAndRestaurant(id, activeRestaurant)
            val receipt = refs.receipt

            val lines = purchaseDao.getLinesForReceipt(id.value)
            val allMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receipt.id)

            if (receipt.status == DocumentStatus.VOIDED.name) {
                historyValidator.validateVoidedHistory(receipt, lines, allMovements)
                return@withTransaction
            }

            if (receipt.status != DocumentStatus.POSTED.name) {
                throw ValidationError.InvalidPurchaseStatusTransition
            }

            historyValidator.validatePostedHistory(receipt, lines, allMovements)

            val originalMovements = allMovements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
            val now = timeProvider.now().toEpochMilli()
            val reversals = originalMovements.map { original ->
                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = activeRestaurant.id,
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

            val affectedIngredients = lines.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            purchaseDao.updateReceipt(receipt.copy(
                status = DocumentStatus.VOIDED.name,
                voidedAt = now,
                updatedAt = now
            ))
        }
    }
}
