package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
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
    private val lineCalculator: PurchaseLineCalculator
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
        val unresolvedActiveLines = activeLines.any { lineCandidate ->
            val match = matches.find { it.lineIndex == lineCandidate.index }
            match == null || match.status != InvoiceLineMatchStatus.CONFIRMED
        }
        
        if (unresolvedActiveLines) {
            blockingIssues.add(MaterializationBlockingIssue.UnresolvedLines)
        }

        val lineProposals = activeLines.mapNotNull { lineCandidate ->
            val match = matches.find { it.lineIndex == lineCandidate.index }
            if (match == null || match.status != InvoiceLineMatchStatus.CONFIRMED) return@mapNotNull null
            
            val ingredientId = match.ingredientId ?: return@mapNotNull null
            val unitOptionId = match.unitOptionId ?: return@mapNotNull null
            val areaId = match.inventoryAreaId ?: return@mapNotNull null
            
            val ingredient = ingredientRepository.getById(ingredientId) ?: return@mapNotNull null
            val unitOption = ingredientRepository.getUnitOption(unitOptionId) ?: return@mapNotNull null
            val area = areaRepository.getById(areaId) ?: return@mapNotNull null
            
            val lineCorrection = lineCandidate.correction
            val quantity = lineCandidate.quantity.effectiveValue(lineCorrection?.quantity) ?: return@mapNotNull null
            val lineTotal = lineCandidate.lineTotal.effectiveValue(lineCorrection?.lineTotal) ?: BigDecimal.ZERO
            val unitPrice = lineCandidate.unitPrice.effectiveValue(lineCorrection?.unitPrice)

            val calculation = lineCalculator.calculate(
                quantityEntered = quantity,
                lineTotal = lineTotal,
                optionFactorToBase = unitOption.factorToBase
            )

            PurchaseInvoiceLineProposal(
                lineIndex = lineCandidate.index,
                ingredientId = ingredientId,
                ingredientName = ingredient.name,
                unitOptionId = unitOptionId,
                unitOptionName = unitOption.displayName,
                areaId = areaId,
                areaName = area.name,
                quantityEntered = quantity,
                quantityBase = calculation.quantityBase,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
                warnings = lineCandidate.warnings
            )
        }

        return PurchaseInvoiceDraftProposal(
            purchaseReceiptId = receiptId,
            parseResultId = parseResult.id,
            sourceDocumentSha256 = ocrResult.sourceDocumentSha256,
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
