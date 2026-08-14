package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
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
import com.miara.cuentame.core.database.dao.PurchaseInvoiceLineMatchDao
import com.miara.cuentame.core.database.dao.PurchaseInvoiceMaterializationDao
import com.miara.cuentame.core.database.dao.PurchaseOcrDao
import com.miara.cuentame.core.database.dao.PurchaseParseDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.dao.SupplierItemMappingDao
import com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceLineOriginEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrPageEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParseResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParsedLineEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.SupplierItemMappingEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.LearnMappingResult
import com.miara.cuentame.core.domain.repository.MappingConflict
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.service.PurchaseInvoiceFingerprinter
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.DuplicateInvoiceCandidate
import com.miara.cuentame.core.model.purchase.MatchIntegrityPolicy
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.purchase.SourceMutationResult
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.effectiveValue
import com.miara.cuentame.core.ocr.parser.matching.InventoryNormalization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
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
    private val lineMatchDao: PurchaseInvoiceLineMatchDao,
    private val mappingDao: SupplierItemMappingDao,
    private val materializationDao: PurchaseInvoiceMaterializationDao,
    private val fingerprinter: PurchaseInvoiceFingerprinter,
    private val duplicateInvoiceDetector: DuplicateInvoiceDetector,
    private val json: Json
) : PurchaseRepository {

    private companion object {
        const val PARSER_ENGINE = "CUENTAME_DETERMINISTIC_V2"
        const val PARSER_SCHEMA_VERSION = 2
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
                    flowOf(null)
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
                flowOf(emptyList())
            } else {
                combine(summaryFlows) { it.toList() }
            }
        }
    }

    override fun observePurchase(id: PurchaseReceiptId): Flow<PurchaseDetails?> {
        return purchaseDao.observeReceiptById(id.value).flatMapLatest { receiptEntity ->
            if (receiptEntity == null) return@flatMapLatest flowOf(null)
            
            val linesFlow = purchaseDao.observeLinesForReceipt(id.value)
            val supplierFlow = if (receiptEntity.supplierId != null) {
                supplierDao.observeById(receiptEntity.supplierId)
            } else {
                flowOf(null)
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
            } catch (e: CancellationException) {
                throw e
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

    override suspend fun acceptDuplicateForPosting(candidate: DuplicateInvoiceCandidate) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val receipt = referenceValidator.validateReceiptOwnership(candidate.currentReceiptId, activeRestaurant)
            val sourceSha = ocrDao.getOcrResultForReceiptSync(receipt.id)?.sourceDocumentSha256
            val authoritative = duplicateInvoiceDetector.find(
                activeRestaurant.id, receipt.id, receipt.supplierId, receipt.invoiceNumber, sourceSha
            )
            require(authoritative == candidate) { "Duplicate condition changed; review it again" }
            val application = materializationDao.getApplicationForReceipt(receipt.id)
                ?: error("Duplicate override requires an invoice draft application")
            materializationDao.upsertApplication(application.copy(
                duplicateOverrideType = candidate.type.name,
                duplicateExistingReceiptId = candidate.existingReceiptId.value,
                duplicateNormalizedInvoiceNumber = candidate.normalizedInvoiceNumber,
                duplicateSourceSha256 = candidate.sourceSha256,
                duplicateOverriddenAt = timeProvider.now().toEpochMilli()
            ))
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
    ): SourceMutationResult {
        var oldLocation: String? = null
        val result = try {
            database.withTransaction {
                if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked

                val activeRestaurant = requireActiveRestaurant()
                val existing = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)

                if (existing.status != DocumentStatus.DRAFT.name) {
                    throw ValidationError.PurchaseNotDraft
                }

                oldLocation = existing.attachmentPath

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
                SourceMutationResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SourceMutationResult.NotFound
        }

        // Clean up old file if it existed, after successful commit
        if (result == SourceMutationResult.Success && oldLocation != null && oldLocation != storedLocation) {
            try {
                documentStore.delete(oldLocation!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort cleanup failure must not fail the attachment operation
            }
        }
        return result
    }

    override suspend fun removeDocument(
        receiptId: PurchaseReceiptId
    ): SourceMutationResult {
        var oldLocation: String? = null
        val result = try {
            database.withTransaction {
                if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
                
                val activeRestaurant = requireActiveRestaurant()
                val existing = referenceValidator.validateReceiptOwnership(receiptId, activeRestaurant)

                if (existing.status != DocumentStatus.DRAFT.name) {
                    throw ValidationError.PurchaseNotDraft
                }

                oldLocation = existing.attachmentPath

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
                SourceMutationResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SourceMutationResult.NotFound
        }

        if (result == SourceMutationResult.Success && oldLocation != null) {
            try {
                documentStore.delete(oldLocation!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort cleanup failure must not fail the removal operation
            }
        }
        return result
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
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(result.purchaseReceiptId)) return@withTransaction SourceMutationResult.SourceLocked
        
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
        SourceMutationResult.Success
    }

    override suspend fun deleteOcrResult(receiptId: PurchaseReceiptId): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        ocrDao.deleteOcrForReceipt(receiptId.value)
        SourceMutationResult.Success
    }

    override fun observeParseResult(receiptId: PurchaseReceiptId): Flow<PurchaseInvoiceParseResult?> {
        return parseDao.observeParseResultForReceipt(receiptId.value).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                parseDao.observeParsedLines(entity.id).map { lineEntities ->
                    val baseResult = json.decodeFromString<PurchaseInvoiceParseResult>(entity.totalsEvidenceJson)
                    val corrections = entity.correctionsJson?.takeIf { c -> c != "null" }?.let { c ->
                        json.decodeFromString<PurchaseInvoiceCorrections>(c)
                    }

                    baseResult.copy(
                        id = entity.id,
                        corrections = corrections,
                        lines = lineEntities.map(::mapParsedLine)
                    )
                }
            }
        }
    }

    override suspend fun getParsedLines(parseResultId: String): List<ParsedInvoiceLineCandidate> {
        return parseDao.getParsedLines(parseResultId).map(::mapParsedLine)
    }

    private fun mapParsedLine(entity: PurchaseInvoiceParsedLineEntity): ParsedInvoiceLineCandidate {
        val baseLine = json.decodeFromString<ParsedInvoiceLineCandidate>(entity.evidenceJson)
        val correction = entity.correctionJson?.takeIf { it != "null" }?.let {
            json.decodeFromString<ParsedInvoiceLineCorrection>(it)
        }
        return baseLine.copy(isIgnored = entity.isIgnored, correction = correction)
    }

    override suspend fun saveParseResult(
        receiptId: PurchaseReceiptId,
        ocrResultId: String,
        sourceDocumentSha256: String,
        result: PurchaseInvoiceParseResult
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        
        val parseId = result.id.ifBlank { idGenerator.newId() }
        
        parseDao.replaceParseResult(
            receiptId = receiptId.value,
            expectedOcrResultId = ocrResultId,
            expectedSourceDocumentSha256 = sourceDocumentSha256,
            ocrDao = ocrDao,
            lineMatchDao = lineMatchDao,
            materializationDao = materializationDao,
            result = PurchaseInvoiceParseResultEntity(
                id = parseId,
                purchaseReceiptId = receiptId.value,
                ocrResultId = ocrResultId,
                sourceDocumentSha256 = sourceDocumentSha256,
                parserEngine = PARSER_ENGINE,
                parserSchemaVersion = PARSER_SCHEMA_VERSION,
                headerEvidenceJson = json.encodeToString(result.supplierNameCandidate),
                totalsEvidenceJson = json.encodeToString(result.copy(lines = emptyList(), corrections = null)),
                correctionsJson = result.corrections?.let { json.encodeToString(it) },
                warningsJson = json.encodeToString(result.warnings),
                processedAt = timeProvider.now().toEpochMilli(),
                reviewedAt = null
            ),
            lines = result.lines.map { line ->
                PurchaseInvoiceParsedLineEntity(
                    parseResultId = parseId,
                    lineIndex = line.index,
                    evidenceJson = json.encodeToString(line.copy(correction = null)),
                    correctionJson = line.correction?.let { json.encodeToString(it) },
                    isIgnored = line.isIgnored
                )
            }
        )
        SourceMutationResult.Success
    }

    override suspend fun deleteParseResult(receiptId: PurchaseReceiptId): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        parseDao.deleteParseResultForReceipt(receiptId.value)
        SourceMutationResult.Success
    }

    override suspend fun updateParsedLine(
        receiptId: PurchaseReceiptId,
        lineIndex: Int,
        isIgnored: Boolean,
        correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        
        val parseResult = parseDao.getParseResultForReceipt(receiptId.value) ?: return@withTransaction SourceMutationResult.NotFound
        parseDao.updateParsedLine(
            parseResultId = parseResult.id,
            lineIndex = lineIndex,
            isIgnored = isIgnored,
            correctionJson = correction?.let { json.encodeToString(it) }
        )
        SourceMutationResult.Success
    }

    override suspend fun addManualParsedLine(
        receiptId: PurchaseReceiptId,
        correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        val parseResult = parseDao.getParseResultForReceipt(receiptId.value)
            ?: return@withTransaction SourceMutationResult.NotFound
        val nextIndex = (parseDao.getParsedLines(parseResult.id).maxOfOrNull { it.lineIndex } ?: -1) + 1
        val manualLine = com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate.manual(nextIndex)
        parseDao.insertParsedLines(
            listOf(
                PurchaseInvoiceParsedLineEntity(
                    parseResultId = parseResult.id,
                    lineIndex = nextIndex,
                    evidenceJson = json.encodeToString(manualLine),
                    correctionJson = json.encodeToString(correction),
                    isIgnored = false
                )
            )
        )
        SourceMutationResult.Success
    }

    override suspend fun updateParseResult(
        receiptId: PurchaseReceiptId,
        corrections: com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        
        parseDao.updateParseResultCorrections(
            receiptId = receiptId.value,
            correctionsJson = corrections.let { json.encodeToString(it) },
            reviewedAt = timeProvider.now().toEpochMilli()
        )
        SourceMutationResult.Success
    }

    override suspend fun findReceiptsByInvoiceNumber(
        restaurantId: RestaurantId,
        invoiceNumber: String
    ): List<PurchaseReceipt> {
        return purchaseDao.findByInvoiceNumber(restaurantId.value, invoiceNumber).map { it.toDomain() }
    }

    override fun observeLineMatches(parseResultId: String): Flow<List<PurchaseInvoiceLineMatch>> {
        return lineMatchDao.observeMatchesForParseResult(parseResultId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeLineMatchesForReceipt(receiptId: PurchaseReceiptId): Flow<List<PurchaseInvoiceLineMatch>> {
        return parseDao.observeParseResultForReceipt(receiptId.value).flatMapLatest { parseResult ->
            if (parseResult != null) {
                observeLineMatches(parseResult.id)
            } else {
                flowOf(emptyList())
            }
        }
    }

    override suspend fun saveLineMatchesForReceipt(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        matches: List<PurchaseInvoiceLineMatch>
    ): SourceMutationResult = database.withTransaction {
        if (isSourceLocked(receiptId)) return@withTransaction SourceMutationResult.SourceLocked
        
        val currentParseResultId = parseDao.getParseResultIdForReceipt(receiptId.value)
        if (currentParseResultId != expectedParseResultId) {
            throw ValidationError.ParseResultChanged
        }

        val activeRestaurant = requireActiveRestaurant()
        val parsedLines = parseDao.getParsedLines(currentParseResultId)
        val validIndices = parsedLines.map { it.lineIndex }.toSet()

        val entities = matches.map { match ->
            if (match.lineIndex !in validIndices) {
                throw ValidationError.InvalidLineIndex
            }
            validateMatchInvariants(match)
            validateMatchIntegrity(activeRestaurant.id, match)
            match.copy(parseResultId = currentParseResultId).toEntity()
        }
        
        lineMatchDao.insertMatches(entities)
        SourceMutationResult.Success
    }

    override suspend fun saveLineMatchForReceipt(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        match: PurchaseInvoiceLineMatch
    ): SourceMutationResult {
        return saveLineMatchesForReceipt(receiptId, expectedParseResultId, listOf(match))
    }

    override suspend fun confirmInvoiceLineMatch(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        expectedSupplierId: SupplierId?,
        lineIndex: Int,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?,
        forceLearnMapping: Boolean
    ): LearnMappingResult = database.withTransaction {
        if (isSourceLocked(receiptId)) {
            throw ValidationError.InvoiceSourceLocked
        }
        
        val activeRestaurant = requireActiveRestaurant()
        
        // 1. Load Purchase and Parse
        val purchase = purchaseDao.getReceiptById(receiptId.value)
            ?: throw ValidationError.PurchaseNotFound
        if (purchase.restaurantId != activeRestaurant.id) throw ValidationError.PurchaseOwnershipMismatch
        
        // Revalidate Supplier Context
        if (purchase.supplierId != expectedSupplierId?.value) {
            throw ValidationError.SupplierOwnershipMismatch
        }

        val parse = parseDao.getParseResultForReceipt(receiptId.value)
            ?: throw ValidationError.ParseResultChanged
        if (parse.id != expectedParseResultId) throw ValidationError.ParseResultChanged
        
        // 2. Load Parsed Line
        val parsedLines = parseDao.getParsedLines(parse.id)
        val lineEntity = parsedLines.find { it.lineIndex == lineIndex }
            ?: throw ValidationError.InvalidLineIndex
        if (lineEntity.isIgnored) throw ValidationError.InvalidMatchStatus
        
        val baseLine = json.decodeFromString<ParsedInvoiceLineCandidate>(lineEntity.evidenceJson)
        val correction = lineEntity.correctionJson?.takeIf { it != "null" }?.let { 
            json.decodeFromString<com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection>(it) 
        }
        
        // 3. Validate Target Invariants for CONFIRMED
        if (unitOptionId == null || inventoryAreaId == null) {
             throw ValidationError.InvalidMatchStatus
        }

        val match = PurchaseInvoiceLineMatch(
            parseResultId = parse.id,
            lineIndex = lineIndex,
            status = InvoiceLineMatchStatus.CONFIRMED,
            supplierId = purchase.supplierId?.let { SupplierId(it) },
            ingredientId = ingredientId,
            unitOptionId = unitOptionId,
            inventoryAreaId = inventoryAreaId,
            mappingId = null,
            matchMethod = "UserSelection",
            matchConfidence = 1.0f,
            confirmedAt = timeProvider.now()
        )
        
        validateMatchIntegrity(activeRestaurant.id, match)
        
        // 4. Upsert CONFIRMED staged match
        lineMatchDao.insertMatches(listOf(match.toEntity()))
        
        // 5. Learn Mapping if Supplier exists
        val supplierId = purchase.supplierId?.let { SupplierId(it) }
        if (supplierId != null) {
            val vendorCode = baseLine.vendorCode.effectiveValue(correction?.vendorCode)
            val description = baseLine.description.effectiveValue(correction?.description)
            val packageText = baseLine.packageText.effectiveValue(correction?.packageText)
            
            val normalizedCode = InventoryNormalization.normalizeVendorCode(vendorCode)
            val mappingParams = if (normalizedCode.isNotEmpty()) {
                SupplierItemMappingKeyType.VENDOR_CODE to normalizedCode
            } else {
                val d = InventoryNormalization.normalizeDescription(description)
                val p = InventoryNormalization.normalizePackageText(packageText)
                SupplierItemMappingKeyType.DESCRIPTION_PACKAGE to "$d|$p"
            }
            
            val keyType = mappingParams.first
            val key = mappingParams.second
            
            val existing = mappingDao.getMapping(activeRestaurant.id, supplierId.value, keyType, key)
            val now = timeProvider.now()
            
            if (existing != null) {
                val isSameTarget = existing.ingredientId == ingredientId.value &&
                        existing.unitOptionId == unitOptionId.value &&
                        existing.inventoryAreaId == inventoryAreaId.value
                
                if (isSameTarget) {
                    mappingDao.insertMapping(existing.copy(lastConfirmedAt = now.toEpochMilli()))
                    return@withTransaction LearnMappingResult.NoChanges
                }
                
                if (!forceLearnMapping) {
                    return@withTransaction LearnMappingResult.Conflict(
                        MappingConflict(
                            existingMapping = existing.toDomain(),
                            newIngredientId = ingredientId,
                            newUnitOptionId = unitOptionId,
                            newInventoryAreaId = inventoryAreaId
                        )
                    )
                }
                
                mappingDao.insertMapping(existing.copy(
                    ingredientId = ingredientId.value,
                    unitOptionId = unitOptionId.value,
                    inventoryAreaId = inventoryAreaId.value,
                    updatedAt = now.toEpochMilli(),
                    lastConfirmedAt = now.toEpochMilli(),
                    sourceVendorCode = vendorCode,
                    sourceDescription = description,
                    sourcePackageText = packageText
                ))
                return@withTransaction LearnMappingResult.Learned
            } else {
                val newMapping = SupplierItemMappingEntity(
                    id = idGenerator.newId(),
                    restaurantId = activeRestaurant.id,
                    supplierId = supplierId.value,
                    keyType = keyType,
                    normalizedKey = key,
                    sourceVendorCode = vendorCode,
                    sourceDescription = description,
                    sourcePackageText = packageText,
                    ingredientId = ingredientId.value,
                    unitOptionId = unitOptionId.value,
                    inventoryAreaId = inventoryAreaId.value,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    lastConfirmedAt = now.toEpochMilli()
                )
                mappingDao.insertMapping(newMapping)
                return@withTransaction LearnMappingResult.Learned
            }
        }
        
        LearnMappingResult.NoChanges
    }

    private fun validateMatchInvariants(match: PurchaseInvoiceLineMatch) {
        val error = MatchIntegrityPolicy.validateInvariants(
            status = match.status,
            ingredientId = match.ingredientId,
            unitOptionId = match.unitOptionId,
            inventoryAreaId = match.inventoryAreaId,
            confirmedAt = match.confirmedAt,
            mappingId = match.mappingId
        )
        if (error != null) throw ValidationError.InvalidMatchStatus
    }

    private suspend fun validateMatchIntegrity(
        restaurantId: String,
        match: PurchaseInvoiceLineMatch
    ) {
        if (match.supplierId != null) {
            val supplier = supplierDao.getById(match.supplierId.value)
                ?: throw ValidationError.SupplierNotFound
            if (supplier.restaurantId != restaurantId) throw ValidationError.SupplierOwnershipMismatch
        }

        if (match.ingredientId != null) {
            val ingredient = ingredientDao.getById(match.ingredientId.value)
                ?: throw ValidationError.IngredientNotFound
            if (ingredient.restaurantId != restaurantId) throw ValidationError.IngredientOwnershipMismatch

            if (match.unitOptionId != null) {
                val option = unitOptionDao.getById(match.unitOptionId.value)
                    ?: throw ValidationError.UnitOptionNotFound
                if (option.ingredientId != match.ingredientId.value) throw ValidationError.InvalidPurchaseUnitOption
            }
        } else if (match.unitOptionId != null) {
            throw ValidationError.InvalidPurchaseUnitOption
        }

        if (match.inventoryAreaId != null) {
            val area = areaDao.getById(match.inventoryAreaId.value)
                ?: throw ValidationError.RecordNotFound
            if (area.restaurantId != restaurantId) throw ValidationError.InvalidPurchaseArea
        }

        if (match.mappingId != null) {
            val mapping = mappingDao.getMappingById(match.mappingId)
                ?: throw ValidationError.RecordNotFound
            
            if (mapping.restaurantId != restaurantId) throw ValidationError.SupplierOwnershipMismatch
            if (match.supplierId != null && mapping.supplierId != match.supplierId.value) throw ValidationError.SupplierOwnershipMismatch
            
            val error = MatchIntegrityPolicy.isMappingCompatible(
                matchIngredientId = match.ingredientId?.value,
                matchUnitOptionId = match.unitOptionId?.value,
                matchAreaId = match.inventoryAreaId?.value,
                mappingIngredientId = mapping.ingredientId,
                mappingUnitOptionId = mapping.unitOptionId,
                mappingAreaId = mapping.inventoryAreaId
            )
            if (error != null) {
                if (error.contains("ingredient")) throw ValidationError.IngredientOwnershipMismatch
                if (error.contains("unit")) throw ValidationError.InvalidPurchaseUnitOption
                if (error.contains("area")) throw ValidationError.InvalidPurchaseArea
            }
        }
    }

    override suspend fun applyInvoiceToDraft(proposal: PurchaseInvoiceDraftProposal): PurchaseInvoiceMaterializationResult = try {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val receiptId = proposal.purchaseReceiptId
            val receipt = getReceipt(receiptId)
                ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.PurchaseNotFound)

            if (receipt.status != DocumentStatus.DRAFT) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.PurchaseAlreadyPosted)
            }

            // --- PHASE A: PREFLIGHT / VALIDATION ---

            // 1. Proposal Structural Integrity (Goal 1 A-E)
            if (proposal.blockingIssues.isNotEmpty()) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.UnresolvedLines)
            }
            if (proposal.lines.isEmpty()) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.UnresolvedLines)
            }
            if (proposal.lines.any { it.blockingReason != null }) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.UnresolvedLines)
            }
            
            val proposalIndices = proposal.lines.map { it.lineIndex }
            if (proposalIndices.size != proposalIndices.toSet().size) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.DraftChanged)
            }

            // 2. Source Integrity (Goal 1 E, Goal 4)
            val currentOcr = observeOcrResult(receiptId).first()
                ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.DocumentMissing)
            if (currentOcr.sourceDocumentSha256 != proposal.sourceDocumentSha256) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.DocumentChanged)
            }

            val currentParse = observeParseResult(receiptId).first()
            if (currentParse == null || (currentParse.id != proposal.parseResultId)) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.ParseChanged)
            }

            // Header Validation (Goal 4)
            val corrections = currentParse.corrections
            val effectiveInvoiceNumber = currentParse.invoiceNumber.effectiveValue(corrections?.invoiceNumber)
            val effectiveInvoiceDate = currentParse.invoiceDate.effectiveValue(corrections?.invoiceDate)

            val duplicate = duplicateInvoiceDetector.find(
                activeRestaurant.id, receiptId.value, receipt.supplierId?.value,
                effectiveInvoiceNumber, currentOcr.sourceDocumentSha256
            )
            if (duplicate != null && proposal.acceptedDuplicate != duplicate) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(
                    PurchaseInvoiceMaterializationFailure.StrongDuplicate(duplicate)
                )
            }

            if (proposal.invoiceNumber != effectiveInvoiceNumber ||
                proposal.invoiceDate != effectiveInvoiceDate) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvoiceStateChanged)
            }

            val activeSourceIndices = currentParse.lines.filter { !it.isIgnored }.map { it.index }.toSet()
            if (proposalIndices.toSet() != activeSourceIndices) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvoiceStateChanged)
            }

            val currentMatches = observeLineMatchesForReceipt(receiptId).first()
            val currentFingerprint = fingerprinter.fingerprint(
                receiptId = receiptId,
                supplierId = receipt.supplierId?.value,
                sourceDocumentSha256 = currentOcr.sourceDocumentSha256,
                parseResult = currentParse,
                matches = currentMatches
            )
            
            if (currentFingerprint != proposal.sourceStateFingerprint) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvoiceStateChanged)
            }

            // Supplier Consistency (Goal 4)
            if (receipt.supplierId == null || proposal.supplierProposal == null || receipt.supplierId.value != proposal.supplierProposal.id.value) {
                return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.SupplierChanged)
            }

            // 3. Line Content Validation & Source Verification (Goal 1 F-I, Goal 3)
            val validatedLines = mutableListOf<ValidatedMaterializationLine>()
            
            for (lineProposal in proposal.lines) {
                // F. Null checks
                val ingId = lineProposal.ingredientId ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.UnresolvedLines)
                val areaId = lineProposal.areaId ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingRequiredArea)
                val unitOptId = lineProposal.unitOptionId ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingRequiredUnitOption)
                val qtyEntered = lineProposal.quantityEntered ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingQuantity)
                val qtyBase = lineProposal.quantityBase ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingQuantity)
                val factorToBase = lineProposal.factorToBase ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingQuantity)
                val lineTotal = lineProposal.lineTotal ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.MissingQuantity)

                // G. Numeric Invariants
                if (qtyEntered <= BigDecimal.ZERO || qtyBase <= BigDecimal.ZERO || factorToBase <= BigDecimal.ZERO || lineTotal < BigDecimal.ZERO) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                }

                // H. Current match consistency
                val match = currentMatches.find { it.lineIndex == lineProposal.lineIndex }
                    ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                
                if (match.status != InvoiceLineMatchStatus.CONFIRMED ||
                    match.ingredientId != ingId ||
                    match.unitOptionId != unitOptId ||
                    match.inventoryAreaId != areaId ||
                    match.supplierId?.value != receipt.supplierId.value) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                }

                // I. Relational Integrity
                val ingredient = ingredientDao.getById(ingId.value) ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                if (ingredient.restaurantId != activeRestaurant.id) return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.PersistenceFailed)
                
                val unitOption = unitOptionDao.getById(unitOptId.value) ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                if (unitOption.ingredientId != ingId.value) return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                
                val area = areaDao.getById(areaId.value) ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                if (area.restaurantId != activeRestaurant.id) return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.PersistenceFailed)

                // Goal 3: Verify against source state
                val sourceLine = currentParse.lines.find { it.index == lineProposal.lineIndex }
                    ?: return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvoiceStateChanged)
                
                val sourceQty = sourceLine.quantity.effectiveValue(sourceLine.correction?.quantity)
                val sourceTotal = sourceLine.lineTotal.effectiveValue(sourceLine.correction?.lineTotal)
                
                if (sourceQty == null || sourceQty.compareTo(qtyEntered) != 0 ||
                    sourceTotal == null || sourceTotal.compareTo(lineTotal) != 0) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvoiceStateChanged)
                }

                if (unitOption.factorToBase.compareTo(factorToBase) != 0) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                }

                val calculation = try {
                    lineCalculator.calculate(qtyEntered, lineTotal, factorToBase)
                } catch (e: Exception) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                }
                
                if (calculation.quantityBase.compareTo(qtyBase) != 0) {
                    return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.InvalidConfirmedMatch)
                }

                validatedLines.add(
                    ValidatedMaterializationLine(
                        lineIndex = lineProposal.lineIndex,
                        ingredientId = ingId,
                        areaId = areaId,
                        unitOptionId = unitOptId,
                        quantityEntered = qtyEntered,
                        quantityBase = qtyBase,
                        factorToBase = factorToBase,
                        lineTotal = lineTotal,
                        unitCostBase = calculation.unitCostBase
                    )
                )
            }

            // 4. Manual Edit Conflict Detection (Goal 6)
            val currentLines = purchaseDao.getLinesForReceipt(receiptId.value)
            val existingApp = materializationDao.getApplicationForReceipt(receiptId.value)
            val applicationId = existingApp?.id ?: idGenerator.newId()
            val existingOrigins = materializationDao.getLineOrigins(applicationId)

            proposal.lines.forEach { lineProposal ->
                val existingOrigin = existingOrigins.find { it.sourceLineIndex == lineProposal.lineIndex }
                val existingLine = existingOrigin?.let { origin -> currentLines.find { it.id == origin.purchaseLineId } }
                
                if (existingLine != null) {
                    if (!existingLine.matchesMaterializationSnapshot(existingOrigin.lastMaterializedSnapshotJson, json)) {
                         return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.ManualEditConflict)
                    }
                }
            }

            val removedOrigins = existingOrigins.filter { origin ->
                proposal.lines.none { it.lineIndex == origin.sourceLineIndex }
            }
            
            removedOrigins.forEach { origin ->
                val lineToRemove = currentLines.find { it.id == origin.purchaseLineId }
                if (lineToRemove != null) {
                    if (!lineToRemove.matchesMaterializationSnapshot(origin.lastMaterializedSnapshotJson, json)) {
                        return@withTransaction PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.ManualEditConflict)
                    }
                }
            }

            // --- PHASE B: MUTATION (Goal 2) ---

            // 1. Update Receipt Header
            updateDraft(
                UpdatePurchaseDraftCommand(
                    receiptId = receiptId,
                    supplierId = proposal.supplierProposal.id,
                    invoiceNumber = proposal.invoiceNumber,
                    purchaseDate = proposal.invoiceDate?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant() ?: receipt.purchaseDate,
                    notes = receipt.notes
                )
            )

            // 2. Application Record
            val acceptedDuplicate = duplicate?.takeIf { proposal.acceptedDuplicate == it }
            val appEntity = PurchaseInvoiceDraftApplicationEntity(
                id = applicationId,
                purchaseReceiptId = receiptId.value,
                parseResultId = proposal.parseResultId,
                sourceDocumentSha256 = proposal.sourceDocumentSha256,
                sourceStateFingerprint = currentFingerprint,
                appliedAt = timeProvider.now().toEpochMilli(),
                duplicateOverrideType = acceptedDuplicate?.type?.name,
                duplicateExistingReceiptId = acceptedDuplicate?.existingReceiptId?.value,
                duplicateNormalizedInvoiceNumber = acceptedDuplicate?.normalizedInvoiceNumber,
                duplicateSourceSha256 = acceptedDuplicate?.sourceSha256,
                duplicateOverriddenAt = acceptedDuplicate?.let { timeProvider.now().toEpochMilli() }
            )
            materializationDao.upsertApplication(appEntity)

            val now = timeProvider.now().toEpochMilli()
            val newOrigins = mutableListOf<PurchaseInvoiceLineOriginEntity>()

            // 3. Line Mutation
            validatedLines.forEach { vLine ->
                val existingOrigin = existingOrigins.find { it.sourceLineIndex == vLine.lineIndex }
                val existingLine = existingOrigin?.let { origin -> currentLines.find { it.id == origin.purchaseLineId } }
                
                val purchaseLineId = existingLine?.id ?: idGenerator.newId()

                val lineEntity = PurchaseLineEntity(
                    id = purchaseLineId,
                    purchaseReceiptId = receiptId.value,
                    ingredientId = vLine.ingredientId.value,
                    areaId = vLine.areaId.value,
                    ingredientUnitOptionId = vLine.unitOptionId.value,
                    quantityEntered = vLine.quantityEntered.toPlainString(),
                    quantityBase = vLine.quantityBase.toPlainString(),
                    lineTotal = vLine.lineTotal.toPlainString(),
                    unitCostBase = vLine.unitCostBase.toPlainString(),
                    notes = existingLine?.notes,
                    createdAt = existingLine?.createdAt ?: now,
                    updatedAt = now
                )
                
                if (existingLine != null) {
                    purchaseDao.updateLine(lineEntity)
                } else {
                    purchaseDao.insertLine(lineEntity)
                }

                val newSnapshot = PurchaseLineMaterializationSnapshot(
                    ingredientId = vLine.ingredientId.value,
                    areaId = vLine.areaId.value,
                    unitOptionId = vLine.unitOptionId.value,
                    quantityEntered = vLine.quantityEntered.stripTrailingZeros().toPlainString(),
                    quantityBase = vLine.quantityBase.stripTrailingZeros().toPlainString(),
                    lineTotal = vLine.lineTotal.stripTrailingZeros().toPlainString(),
                    unitCostBase = vLine.unitCostBase.stripTrailingZeros().toPlainString()
                )

                newOrigins.add(
                    PurchaseInvoiceLineOriginEntity(
                        purchaseLineId = purchaseLineId,
                        applicationId = applicationId,
                        sourceLineIndex = vLine.lineIndex,
                        sourceStateFingerprint = currentFingerprint,
                        lastMaterializedSnapshotJson = json.encodeToString(newSnapshot)
                    )
                )
            }
            
            // 4. Obsolete Line Deletion
            removedOrigins.forEach { origin ->
                purchaseDao.deleteLine(origin.purchaseLineId)
                materializationDao.deleteLineOrigin(origin.purchaseLineId)
            }
            
            materializationDao.upsertLineOrigins(newOrigins)
            
            PurchaseInvoiceMaterializationResult.Success
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.PersistenceFailed)
    }

    private data class ValidatedMaterializationLine(
        val lineIndex: Int,
        val ingredientId: IngredientId,
        val areaId: InventoryAreaId,
        val unitOptionId: IngredientUnitOptionId,
        val quantityEntered: BigDecimal,
        val quantityBase: BigDecimal,
        val factorToBase: BigDecimal,
        val lineTotal: BigDecimal,
        val unitCostBase: BigDecimal
    )

    private suspend fun isSourceLocked(receiptId: PurchaseReceiptId): Boolean {
        return materializationDao.getApplicationForReceipt(receiptId.value) != null
    }
}
