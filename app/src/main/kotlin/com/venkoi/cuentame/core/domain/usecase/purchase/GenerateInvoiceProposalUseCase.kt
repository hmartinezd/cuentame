package com.venkoi.cuentame.core.domain.usecase.purchase

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.domain.repository.IngredientRepository
import com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository
import com.venkoi.cuentame.core.domain.repository.PurchaseRepository
import com.venkoi.cuentame.core.domain.repository.SupplierRepository
import com.venkoi.cuentame.core.domain.repository.UnitRepository
import com.venkoi.cuentame.core.domain.service.PurchaseInvoiceFingerprinter
import com.venkoi.cuentame.core.domain.service.PurchaseLineCalculator
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.venkoi.cuentame.core.model.purchase.MatchIntegrityPolicy
import com.venkoi.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import com.venkoi.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.venkoi.cuentame.core.model.purchase.materialization.PurchaseInvoiceLineProposal
import com.venkoi.cuentame.core.model.purchase.materialization.SupplierProposal
import com.venkoi.cuentame.core.ocr.parser.effectiveValue
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

class GenerateInvoiceProposalUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val ingredientRepository: IngredientRepository,
    private val supplierRepository: SupplierRepository,
    private val areaRepository: InventoryAreaRepository,
    private val unitRepository: UnitRepository,
    private val lineCalculator: PurchaseLineCalculator,
    private val fingerprinter: PurchaseInvoiceFingerprinter
) {

    suspend fun execute(receiptId: PurchaseReceiptId): PurchaseInvoiceDraftProposal? {
        val receipt = purchaseRepository.getReceipt(receiptId) ?: return null
        val parseResult = purchaseRepository.observeParseResult(receiptId).first() ?: return null
        val ocrResult = purchaseRepository.observeOcrResult(receiptId).first() ?: return null
        val matches = purchaseRepository.observeLineMatchesForReceipt(receiptId).first()

        val blockingIssues = mutableListOf<MaterializationBlockingIssue>()
        
        if (receipt.status != DocumentStatus.DRAFT) {
            blockingIssues.add(MaterializationBlockingIssue.PurchaseAlreadyPosted)
        }
        
        if (receipt.attachmentPath == null) {
            blockingIssues.add(MaterializationBlockingIssue.DocumentChanged)
        }

        if (receipt.supplierId == null) {
            blockingIssues.add(MaterializationBlockingIssue.MissingSupplier)
        }

        val corrections = parseResult.corrections
        val effectiveInvoiceNumber = parseResult.invoiceNumber.effectiveValue(corrections?.invoiceNumber)
        val effectiveInvoiceDate = parseResult.invoiceDate.effectiveValue(corrections?.invoiceDate)
        val effectiveSubtotal = parseResult.subtotal.effectiveValue(corrections?.subtotal)
        val effectiveDiscount = parseResult.discount.effectiveValue(corrections?.discount)
        val effectiveFees = parseResult.fees.effectiveValue(corrections?.fees)
        val effectiveTax = parseResult.tax.effectiveValue(corrections?.tax)
        val effectiveTotal = parseResult.total.effectiveValue(corrections?.total)

        val supplierProposal = receipt.supplierId?.let { supplierId ->
            supplierRepository.getSupplier(supplierId)?.let { SupplierProposal(it.id, it.name) }
        }

        val activeLines = parseResult.lines.filter { !it.isIgnored }
        
        val lineProposals = activeLines.map { lineCandidate ->
            val match = matches.find { it.lineIndex == lineCandidate.index }
            
            val lineCorrection = lineCandidate.correction
            val quantity = lineCandidate.quantity.effectiveValue(lineCorrection?.quantity)
            val lineTotal = lineCandidate.lineTotal.effectiveValue(lineCorrection?.lineTotal)
            val unitPrice = lineCandidate.unitPrice.effectiveValue(lineCorrection?.unitPrice)

            var blockingReason: MaterializationBlockingIssue? = null

            // 1. Check Match Status FIRST
            if (match == null || match.status != InvoiceLineMatchStatus.CONFIRMED) {
                blockingReason = MaterializationBlockingIssue.UnresolvedMatch
            }

            // 2. Resolve Relational Data
            val ingredientId = match?.ingredientId
            val unitOptionId = match?.unitOptionId
            val areaId = match?.inventoryAreaId
            
            val ingredient = ingredientId?.let { ingredientRepository.getById(it) }
            val unitOption = unitOptionId?.let { ingredientRepository.getUnitOption(it) }
            val area = areaId?.let { areaRepository.getById(it) }
            val baseUnit = ingredient?.let { unitRepository.getById(it.baseUnitId) }

            // 3. Validate Relational Data if match was supposedly confirmed
            if (blockingReason == null) {
                if (ingredient == null) {
                    blockingReason = MaterializationBlockingIssue.MissingIngredient
                } else if (unitOption == null) {
                    blockingReason = MaterializationBlockingIssue.MissingUnitOption
                } else if (unitOption.ingredientId != ingredient.id) {
                    blockingReason = MaterializationBlockingIssue.InvalidUnitOption
                } else if (area == null) {
                    blockingReason = MaterializationBlockingIssue.MissingArea
                }
            }

            // 4. Validate Source Data
            if (blockingReason == null) {
                if (quantity == null) {
                    blockingReason = MaterializationBlockingIssue.MissingQuantity
                } else if (quantity <= BigDecimal.ZERO) {
                    blockingReason = MaterializationBlockingIssue.InvalidQuantity
                } else if (lineTotal == null) {
                    blockingReason = MaterializationBlockingIssue.MissingLineTotal
                } else if (lineTotal < BigDecimal.ZERO) {
                    blockingReason = MaterializationBlockingIssue.InvalidLineTotal
                }
            }
            
            // 5. Calculate
            val calculation = if (blockingReason == null && ingredient != null && unitOption != null && quantity != null && lineTotal != null) {
                try {
                    lineCalculator.calculate(
                        quantityEntered = quantity,
                        lineTotal = lineTotal,
                        optionFactorToBase = unitOption.factorToBase
                    )
                } catch (e: ValidationError.InvalidPurchaseQuantity) {
                    blockingReason = MaterializationBlockingIssue.InvalidQuantity
                    null
                } catch (e: Exception) {
                    blockingReason = MaterializationBlockingIssue.InvalidConversion
                    null
                }
            } else {
                null
            }

            // Final sanity check for calculation
            if (blockingReason == null && calculation == null) {
                blockingReason = MaterializationBlockingIssue.InvalidConversion
            }

            PurchaseInvoiceLineProposal(
                lineIndex = lineCandidate.index,
                ingredientId = ingredientId,
                ingredientName = ingredient?.name,
                unitOptionId = unitOptionId,
                unitOptionName = unitOption?.displayName,
                areaId = areaId,
                areaName = area?.name,
                quantityEntered = quantity,
                quantityBase = calculation?.quantityBase,
                factorToBase = unitOption?.factorToBase,
                baseUnitSymbol = baseUnit?.symbol,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
                warnings = lineCandidate.warnings,
                blockingReason = blockingReason
            )
        }

        if (lineProposals.any { it.blockingReason != null }) {
            if (!blockingIssues.contains(MaterializationBlockingIssue.UnresolvedLines)) {
                blockingIssues.add(MaterializationBlockingIssue.UnresolvedLines)
            }
        }

        val fingerprint = fingerprinter.fingerprint(
            receiptId = receiptId,
            supplierId = receipt.supplierId?.value,
            sourceDocumentSha256 = ocrResult.sourceDocumentSha256,
            parseResult = parseResult,
            matches = matches
        )

        return PurchaseInvoiceDraftProposal(
            purchaseReceiptId = receiptId,
            parseResultId = parseResult.id,
            sourceDocumentSha256 = ocrResult.sourceDocumentSha256,
            sourceStateFingerprint = fingerprint,
            supplierProposal = supplierProposal,
            invoiceNumber = effectiveInvoiceNumber,
            invoiceDate = effectiveInvoiceDate,
            subtotal = effectiveSubtotal,
            discount = effectiveDiscount,
            fees = effectiveFees,
            tax = effectiveTax,
            total = effectiveTotal,
            lines = lineProposals,
            warnings = parseResult.warnings,
            blockingIssues = blockingIssues
        )
    }
}
