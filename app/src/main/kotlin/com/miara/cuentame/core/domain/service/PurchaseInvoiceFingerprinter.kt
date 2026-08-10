package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.effectiveValue
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject

class PurchaseInvoiceFingerprinter @Inject constructor() {

    /**
     * Generates a deterministic fingerprint of the business-relevant staging state
     * that produces a materialization proposal.
     *
     * Includes:
     * - Receipt & Document Identity
     * - Effective Header Values
     * - All Line Definitions (Vendor Code, Description, Package, Quantities, Prices)
     * - Current Match Resolutions (Ingredient, Unit, Area)
     * - Line Status (Active/Ignored)
     */
    fun fingerprint(
        receiptId: PurchaseReceiptId,
        supplierId: String?,
        sourceDocumentSha256: String,
        parseResult: PurchaseInvoiceParseResult,
        matches: List<PurchaseInvoiceLineMatch>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        
        fun update(key: String, value: Any?) {
            digest.update("$key:${value?.toString() ?: "null"}|".toByteArray(Charsets.UTF_8))
        }

        // 1. Identity & Context
        update("receiptId", receiptId.value)
        update("supplierId", supplierId)
        update("docSha256", sourceDocumentSha256)
        update("parseResultId", parseResult.id)

        val headerCorrections = parseResult.corrections
        
        // 2. Header Values
        update("invoiceNumber", parseResult.invoiceNumber.effectiveValue(headerCorrections?.invoiceNumber))
        update("invoiceDate", parseResult.invoiceDate.effectiveValue(headerCorrections?.invoiceDate))
        update("subtotal", parseResult.subtotal.effectiveValue(headerCorrections?.subtotal)?.toCanonicalString())
        update("discount", parseResult.discount.effectiveValue(headerCorrections?.discount)?.toCanonicalString())
        update("fees", parseResult.fees.effectiveValue(headerCorrections?.fees)?.toCanonicalString())
        update("tax", parseResult.tax.effectiveValue(headerCorrections?.tax)?.toCanonicalString())
        update("total", parseResult.total.effectiveValue(headerCorrections?.total)?.toCanonicalString())

        // 3. Line Data & Matches
        // We iterate over ALL lines to ensure that changing a line from active to ignored (or vice versa)
        // changes the fingerprint. We sort by index for determinism.
        val allLines = parseResult.lines.sortedBy { it.index }

        allLines.forEach { line ->
            val match = matches.find { it.lineIndex == line.index }
            val correction = line.correction
            
            update("lineIdx", line.index)
            update("lineIgnored", line.isIgnored)
            
            // Raw/Corrected Identity
            update("vendorCode", line.vendorCode.effectiveValue(correction?.vendorCode))
            update("description", line.description.effectiveValue(correction?.description))
            update("packageText", line.packageText.effectiveValue(correction?.packageText))
            
            // Financials/Quantities
            update("qty", line.quantity.effectiveValue(correction?.quantity)?.toCanonicalString())
            update("price", line.unitPrice.effectiveValue(correction?.unitPrice)?.toCanonicalString())
            update("lineTotal", line.lineTotal.effectiveValue(correction?.lineTotal)?.toCanonicalString())
            
            // Resolution
            update("matchStatus", match?.status?.name)
            update("matchSupplier", match?.supplierId?.value)
            update("matchIngredient", match?.ingredientId?.value)
            update("matchUnit", match?.unitOptionId?.value)
            update("matchArea", match?.inventoryAreaId?.value)
            update("matchMapping", match?.mappingId)
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun BigDecimal.toCanonicalString(): String {
        return stripTrailingZeros().toPlainString()
    }
}
