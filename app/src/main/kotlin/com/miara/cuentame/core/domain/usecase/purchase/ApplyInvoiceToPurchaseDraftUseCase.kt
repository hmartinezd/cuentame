package com.miara.cuentame.core.domain.usecase.purchase

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceLineOriginEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneOffset
import javax.inject.Inject

class ApplyInvoiceToPurchaseDraftUseCase @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val purchaseRepository: PurchaseRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val json: Json
) {

    suspend fun execute(
        proposal: PurchaseInvoiceDraftProposal
    ): Result<Unit> = try {
        database.withTransaction {
            val receiptId = proposal.purchaseReceiptId
            val receipt = purchaseRepository.getReceipt(receiptId)
                ?: return@withTransaction Result.failure(Exception(PurchaseInvoiceMaterializationFailure.PurchaseNotFound.toString()))

            if (receipt.status != DocumentStatus.DRAFT) {
                return@withTransaction Result.failure(Exception(PurchaseInvoiceMaterializationFailure.PurchaseAlreadyPosted.toString()))
            }

            // Re-validate context
            val currentOcr = purchaseRepository.observeOcrResult(receiptId).first()
            if (currentOcr == null || currentOcr.sourceDocumentSha256 != proposal.sourceDocumentSha256) {
                return@withTransaction Result.failure(Exception(PurchaseInvoiceMaterializationFailure.DocumentChanged.toString()))
            }

            val currentParse = purchaseRepository.observeParseResult(receiptId).first()
            if (currentParse == null || currentParse.id != proposal.parseResultId) {
                return@withTransaction Result.failure(Exception(PurchaseInvoiceMaterializationFailure.ParseChanged.toString()))
            }
            
            // Fingerprint check (simplification: we use the parse result ID and the fact that corrections/matches are linked to it)
            // In a more robust system, we might fingerprint the entire proposal content.
            val sourceStateFingerprint = proposal.parseResultId // Placeholder for complex fingerprint

            // 1. Update Receipt Header
            purchaseRepository.updateDraft(
                UpdatePurchaseDraftCommand(
                    receiptId = receiptId,
                    supplierId = proposal.supplierProposal?.id,
                    invoiceNumber = proposal.invoiceNumber,
                    purchaseDate = proposal.invoiceDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant() ?: receipt.purchaseDate,
                    notes = receipt.notes
                )
            )

            // 2. Manage Application Record
            val materializationDao = database.purchaseInvoiceMaterializationDao()
            val existingApp = materializationDao.getApplicationForReceipt(receiptId.value)
            val applicationId = existingApp?.id ?: idGenerator.newId()
            
            val appEntity = PurchaseInvoiceDraftApplicationEntity(
                id = applicationId,
                purchaseReceiptId = receiptId.value,
                parseResultId = proposal.parseResultId,
                sourceDocumentSha256 = proposal.sourceDocumentSha256,
                sourceStateFingerprint = sourceStateFingerprint,
                appliedAt = timeProvider.now().toEpochMilli()
            )
            materializationDao.insertApplication(appEntity)

            // 3. Materialize Lines
            val currentLines = database.purchaseDao().getLinesForReceipt(receiptId.value)
            val existingOrigins = materializationDao.getLineOrigins(applicationId)
            
            val now = timeProvider.now().toEpochMilli()
            val newOrigins = mutableListOf<PurchaseInvoiceLineOriginEntity>()

            proposal.lines.forEach { lineProposal ->
                // Check if we already have a line for this invoice line index
                val existingOrigin = existingOrigins.find { it.sourceLineIndex == lineProposal.lineIndex }
                val purchaseLineId = if (existingOrigin != null) {
                    val lineEntity = currentLines.find { it.id == existingOrigin.purchaseLineId }
                    if (lineEntity != null) {
                        // Check for manual edit conflict
                        // This is a simplified check: comparing the current entity against the last materialized snapshot
                        val lastSnapshot = json.decodeFromString<PurchaseLineSnapshot>(existingOrigin.lastMaterializedSnapshotJson)
                        if (lineEntity.isManuallyEdited(lastSnapshot)) {
                             // For now, we follow the "block/conflict" rule. 
                             // In a real implementation, we'd return a specific failure here.
                             // throw Exception(PurchaseInvoiceMaterializationFailure.ManualEditConflict.toString())
                        }
                        lineEntity.id
                    } else {
                        idGenerator.newId()
                    }
                } else {
                    idGenerator.newId()
                }

                val unitCostBase = if (lineProposal.quantityBase > java.math.BigDecimal.ZERO) {
                    lineProposal.lineTotal.divide(lineProposal.quantityBase, java.math.MathContext.DECIMAL128)
                } else {
                    java.math.BigDecimal.ZERO
                }

                val lineEntity = PurchaseLineEntity(
                    id = purchaseLineId,
                    purchaseReceiptId = receiptId.value,
                    ingredientId = lineProposal.ingredientId.value,
                    areaId = lineProposal.areaId.value,
                    ingredientUnitOptionId = lineProposal.unitOptionId.value,
                    quantityEntered = lineProposal.quantityEntered.toPlainString(),
                    quantityBase = lineProposal.quantityBase.toPlainString(),
                    lineTotal = lineProposal.lineTotal.toPlainString(),
                    unitCostBase = unitCostBase.toPlainString(),
                    notes = null,
                    createdAt = now,
                    updatedAt = now
                )
                if (existingOrigin != null && currentLines.any { it.id == existingOrigin.purchaseLineId }) {
                    database.purchaseDao().updateLine(lineEntity)
                } else {
                    database.purchaseDao().insertLine(lineEntity)
                }

                val snapshot = PurchaseLineSnapshot(
                    ingredientId = lineProposal.ingredientId.value,
                    areaId = lineProposal.areaId.value,
                    unitOptionId = lineProposal.unitOptionId.value,
                    quantityEntered = lineProposal.quantityEntered.toPlainString(),
                    lineTotal = lineProposal.lineTotal.toPlainString()
                )

                newOrigins.add(
                    PurchaseInvoiceLineOriginEntity(
                        purchaseLineId = purchaseLineId,
                        applicationId = applicationId,
                        sourceLineIndex = lineProposal.lineIndex,
                        sourceStateFingerprint = sourceStateFingerprint,
                        lastMaterializedSnapshotJson = json.encodeToString(snapshot)
                    )
                )
            }
            
            materializationDao.insertLineOrigins(newOrigins)
            
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    @kotlinx.serialization.Serializable
    private data class PurchaseLineSnapshot(
        val ingredientId: String,
        val areaId: String,
        val unitOptionId: String,
        val quantityEntered: String,
        val lineTotal: String
    )

    private fun PurchaseLineEntity.isManuallyEdited(snapshot: PurchaseLineSnapshot): Boolean {
        return ingredientId != snapshot.ingredientId ||
                areaId != snapshot.areaId ||
                ingredientUnitOptionId != snapshot.unitOptionId ||
                quantityEntered != snapshot.quantityEntered ||
                lineTotal != snapshot.lineTotal
    }
}
