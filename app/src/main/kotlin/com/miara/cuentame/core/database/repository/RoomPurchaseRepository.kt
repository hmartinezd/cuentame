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
import com.miara.cuentame.core.database.dao.PurchaseOcrDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
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
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.database.dao.PurchaseParseDao
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrPageEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParseResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParsedLineEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
    private val timeProvider: TimeProvider,
    private val activeRestaurantProvider: ActiveRestaurantProvider,
    private val postingCoordinator: PurchasePostingCoordinator,
    private val voidingCoordinator: PurchaseVoidingCoordinator,
    private val documentStore: PurchaseDocumentStore,
    private val ocrDao: PurchaseOcrDao,
    private val parseDao: PurchaseParseDao,
    private val json: Json
) : PurchaseRepository {

    private companion object {
        const val PARSER_ENGINE = "CUENTAME_DETERMINISTIC_V1"
        const val PARSER_SCHEMA_VERSION = 1
    }

    private suspend fun requireActiveRestaurant(): RestaurantEntity {
        return activeRestaurantProvider.getActiveRestaurant()
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

        referenceValidator.validateSupplierForDraft(command.supplierId, activeRestaurant.id)

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
            val existing = referenceValidator.validateReceiptOwnership(command.receiptId, activeRestaurant)
            
            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.PurchaseNotDraft
            }

            referenceValidator.validateSupplierForDraft(command.supplierId, activeRestaurant.id)

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
                command.ingredientUnitOptionId,
                requireActive = true
            )

            val receipt = referenceValidator.validateReceiptOwnership(command.receiptId, activeRestaurant)
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
            val receipt = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)
            
            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val line = purchaseDao.getLineById(lineId.value)
                ?: throw ValidationError.PurchaseLineNotFound
            if (line.purchaseReceiptId != receiptId.value) throw ValidationError.PurchaseLineOwnershipMismatch

            purchaseDao.deleteLine(lineId.value)
        }
    }

    override suspend fun deleteDraft(id: PurchaseReceiptId) {
        val attachmentPath = database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val receipt = referenceValidator.validateReceiptOwnership(id, activeRestaurant)

            if (receipt.status != DocumentStatus.DRAFT.name) throw ValidationError.PurchaseNotDraft

            val path = receipt.attachmentPath

            purchaseDao.deleteDraftWithLines(id.value)

            path
        }

        if (attachmentPath != null) {
            try {
                documentStore.delete(attachmentPath)
            } catch (e: Exception) {
                // Best-effort cleanup failure must not fail the draft deletion
            }
        }
    }

    override suspend fun post(id: PurchaseReceiptId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            postingCoordinator.post(id, activeRestaurant)
        }
    }

    override suspend fun void(id: PurchaseReceiptId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            voidingCoordinator.void(id, activeRestaurant)
        }
    }

    override suspend fun attachDocument(
        receiptId: PurchaseReceiptId,
        storedLocation: String,
        displayName: String
    ) {
        val oldLocation = database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)

            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.PurchaseNotDraft
            }

            val previousPath = existing.attachmentPath

            val updated = existing.copy(
                attachmentPath = storedLocation,
                attachmentDisplayName = displayName,
                updatedAt = timeProvider.now().toEpochMilli()
            )

            val affected = purchaseDao.updateReceipt(updated)
            if (affected != 1) {
                throw ValidationError.PurchaseNotFound
            }

            ocrDao.deleteOcrForReceipt(receiptId.value)

            previousPath
        }

        // Clean up old file if it existed, after successful commit
        if (oldLocation != null && oldLocation != storedLocation) {
            try {
                documentStore.delete(oldLocation)
            } catch (e: Exception) {
                // Best-effort cleanup failure must not fail the attachment operation
            }
        }
    }

    override suspend fun removeDocument(
        receiptId: PurchaseReceiptId
    ) {
        val oldLocation = database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)

            if (existing.status != DocumentStatus.DRAFT.name) {
                throw ValidationError.PurchaseNotDraft
            }

            val previousPath = existing.attachmentPath

            val updated = existing.copy(
                attachmentPath = null,
                attachmentDisplayName = null,
                updatedAt = timeProvider.now().toEpochMilli()
            )

            val affected = purchaseDao.updateReceipt(updated)
            if (affected != 1) {
                throw ValidationError.PurchaseNotFound
            }

            ocrDao.deleteOcrForReceipt(receiptId.value)

            previousPath
        }

        if (oldLocation != null) {
            try {
                documentStore.delete(oldLocation)
            } catch (e: Exception) {
                // Best-effort cleanup failure must not fail the removal operation
            }
        }
    }

    override fun observeOcrResult(receiptId: PurchaseReceiptId): Flow<PurchaseInvoiceOcrResult?> {
        return ocrDao.getOcrResultForReceipt(receiptId.value).map { entity ->
            entity?.let {
                PurchaseInvoiceOcrResult(
                    id = it.id,
                    purchaseReceiptId = PurchaseReceiptId(it.purchaseReceiptId),
                    sourceDocumentSha256 = it.sourceDocumentSha256,
                    sourceMimeType = it.sourceMimeType,
                    engine = it.engine,
                    evidenceSchemaVersion = it.evidenceSchemaVersion,
                    pageCount = it.pageCount,
                    fullText = it.fullText,
                    processedAt = Instant.ofEpochMilli(it.processedAt)
                )
            }
        }
    }

    override suspend fun getOcrPages(resultId: String): List<PurchaseInvoiceOcrPage> {
        return ocrDao.getOcrPagesSync(resultId).map { entity ->
            PurchaseInvoiceOcrPage(
                ocrResultId = entity.ocrResultId,
                pageIndex = entity.pageIndex,
                widthPx = entity.widthPx,
                heightPx = entity.heightPx,
                text = entity.text,
                evidence = json.decodeFromString(entity.evidenceJson)
            )
        }
    }

    override suspend fun saveOcrResult(
        result: PurchaseInvoiceOcrResult,
        pages: List<PurchaseInvoiceOcrPage>,
        expectedAttachmentPath: String,
        expectedDocumentSha256: String
    ) {
        ocrDao.replaceOcrResult(
            receiptId = result.purchaseReceiptId.value,
            expectedAttachmentPath = expectedAttachmentPath,
            expectedDocumentSha256 = expectedDocumentSha256,
            purchaseDao = purchaseDao,
            result = PurchaseInvoiceOcrResultEntity(
                id = result.id,
                purchaseReceiptId = result.purchaseReceiptId.value,
                sourceDocumentSha256 = result.sourceDocumentSha256,
                sourceMimeType = result.sourceMimeType,
                engine = result.engine,
                evidenceSchemaVersion = result.evidenceSchemaVersion,
                pageCount = result.pageCount,
                fullText = result.fullText,
                processedAt = result.processedAt.toEpochMilli()
            ),
            pages = pages.map { page ->
                PurchaseInvoiceOcrPageEntity(
                    ocrResultId = page.ocrResultId,
                    pageIndex = page.pageIndex,
                    widthPx = page.widthPx,
                    heightPx = page.heightPx,
                    text = page.text,
                    evidenceJson = json.encodeToString(page.evidence)
                )
            }
        )
    }

    override suspend fun deleteOcrResult(receiptId: PurchaseReceiptId) {
        ocrDao.deleteOcrForReceipt(receiptId.value)
    }

    override fun observeParseResult(receiptId: PurchaseReceiptId): Flow<PurchaseInvoiceParseResult?> {
        return parseDao.observeParseResultForReceipt(receiptId.value).map { entity ->
            entity?.let {
                val baseResult = json.decodeFromString<PurchaseInvoiceParseResult>(it.totalsEvidenceJson)
                val corrections = it.correctionsJson?.let { c -> json.decodeFromString<PurchaseInvoiceParseResult>(c) }
                
                // Merge or return corrections if present
                val result = corrections ?: baseResult
                result.copy(lines = getParsedLines(it.id))
            }
        }
    }

    override suspend fun getParsedLines(parseResultId: String): List<ParsedInvoiceLineCandidate> {
        return parseDao.getParsedLines(parseResultId).map { entity ->
            val baseLine = json.decodeFromString<ParsedInvoiceLineCandidate>(entity.evidenceJson)
            val correction = entity.correctionJson?.let { json.decodeFromString<ParsedInvoiceLineCandidate>(it) }
            
            (correction ?: baseLine).copy(isIgnored = entity.isIgnored)
        }
    }

    override suspend fun saveParseResult(
        receiptId: PurchaseReceiptId,
        ocrResultId: String,
        sourceDocumentSha256: String,
        result: PurchaseInvoiceParseResult
    ) {
        val parseId = idGenerator.newId()
        
        parseDao.replaceParseResult(
            receiptId = receiptId.value,
            ocrResultId = ocrResultId,
            result = PurchaseInvoiceParseResultEntity(
                id = parseId,
                purchaseReceiptId = receiptId.value,
                ocrResultId = ocrResultId,
                sourceDocumentSha256 = sourceDocumentSha256,
                parserEngine = PARSER_ENGINE,
                parserSchemaVersion = PARSER_SCHEMA_VERSION,
                headerEvidenceJson = json.encodeToString(result.supplierNameCandidate),
                totalsEvidenceJson = json.encodeToString(result.copy(lines = emptyList())),
                correctionsJson = null,
                warningsJson = json.encodeToString(result.warnings),
                processedAt = timeProvider.now().toEpochMilli(),
                reviewedAt = null
            ),
            lines = result.lines.map { line ->
                PurchaseInvoiceParsedLineEntity(
                    parseResultId = parseId,
                    lineIndex = line.index,
                    evidenceJson = json.encodeToString(line),
                    correctionJson = null,
                    isIgnored = line.isIgnored
                )
            }
        )
    }

    override suspend fun deleteParseResult(receiptId: PurchaseReceiptId) {
        parseDao.deleteParseResultForReceipt(receiptId.value)
    }

    override suspend fun updateParsedLine(
        receiptId: PurchaseReceiptId,
        lineIndex: Int,
        isIgnored: Boolean,
        correction: ParsedInvoiceLineCandidate?
    ) {
        val parseResult = parseDao.getParseResultForReceipt(receiptId.value) ?: return
        parseDao.updateParsedLine(
            parseResultId = parseResult.id,
            lineIndex = lineIndex,
            isIgnored = isIgnored,
            correctionJson = correction?.let { json.encodeToString(it) }
        )
    }

    override suspend fun updateParseResult(
        receiptId: PurchaseReceiptId,
        corrections: PurchaseInvoiceParseResult
    ) {
        parseDao.updateParseResultCorrections(
            receiptId = receiptId.value,
            correctionsJson = json.encodeToString(corrections),
            reviewedAt = timeProvider.now().toEpochMilli()
        )
    }
}
