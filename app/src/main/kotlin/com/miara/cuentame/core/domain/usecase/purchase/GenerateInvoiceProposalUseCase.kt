package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.service.PurchaseInvoiceFingerprinter
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.MatchIntegrityPolicy
import com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceLineProposal
import com.miara.cuentame.core.model.purchase.materialization.SupplierProposal
import com.miara.cuentame.core.ocr.parser.effectiveValue
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
            
            val ingredientId = match?.ingredientId
            val unitOptionId = match?.unitOptionId
            val areaId = match?.inventoryAreaId
            
            val ingredient = ingredientId?.let { ingredientRepository.getById(it) }
            val unitOption = unitOptionId?.let { ingredientRepository.getUnitOption(it) }
            val area = areaId?.let { areaRepository.getById(it) }
            val baseUnit = ingredient?.let { unitRepository.getById(it.baseUnitId) }
            
            val lineCorrection = lineCandidate.correction
            val quantity = lineCandidate.quantity.effectiveValue(lineCorrection?.quantity)
            val lineTotal = lineCandidate.lineTotal.effectiveValue(lineCorrection?.lineTotal)
            val unitPrice = lineCandidate.unitPrice.effectiveValue(lineCorrection?.unitPrice)

            var blockingReason: MaterializationBlockingIssue? = null
            
            val calculation = if (ingredient != null && unitOption != null && area != null && quantity != null && lineTotal != null) {
                try {
                    lineCalculator.calculate(
                        quantityEntered = quantity,
                        lineTotal = lineTotal,
                        optionFactorToBase = unitOption.factorToBase
                    )
                } catch (e: ValidationError.InvalidPurchaseQuantity) {
                    blockingReason = MaterializationBlockingIssue.UnresolvedLines // Reuse or add specific
                    null
                } catch (e: Exception) {
                    null
                }
            } else {
                if (match == null || match.status != InvoiceLineMatchStatus.CONFIRMED) {
                    blockingReason = MaterializationBlockingIssue.UnresolvedLines
                } else if (ingredient == null || unitOption == null || area == null) {
                    blockingReason = MaterializationBlockingIssue.ParseChanged // Or broken data
                } else if (quantity == null) {
                    // Specific missing data
                }
                null
            }

            PurchaseInvoiceLineProposal(
                lineIndex = lineCandidate.index,
                ingredientId = ingredientId ?: IngredientId(""),
                ingredientName = ingredient?.name ?: "",
                unitOptionId = unitOptionId ?: IngredientUnitOptionId(""),
                unitOptionName = unitOption?.displayName ?: "",
                areaId = areaId ?: InventoryAreaId(""),
                areaName = area?.name ?: "",
                quantityEntered = quantity ?: BigDecimal.ZERO,
                quantityBase = calculation?.quantityBase ?: BigDecimal.ZERO,
                factorToBase = unitOption?.factorToBase ?: BigDecimal.ONE,
                baseUnitSymbol = baseUnit?.symbol ?: "",
                unitPrice = unitPrice,
                lineTotal = lineTotal ?: BigDecimal.ZERO,
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
