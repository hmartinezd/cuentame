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
     */
    fun fingerprint(
        receiptId: PurchaseReceiptId,
        supplierId: String?,
        sourceDocumentSha256: String,
        parseResult: PurchaseInvoiceParseResult,
        matches: List<PurchaseInvoiceLineMatch>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        
        // Context
        digest.update("receipt:${receiptId.value}".toByteArray())
        digest.update("supplier:${supplierId ?: "null"}".toByteArray())
        digest.update("doc:${sourceDocumentSha256}".toByteArray())
        digest.update("parse:${parseResult.id}".toByteArray())

        val headerCorrections = parseResult.corrections
        
        // Header Values
        digest.update("invoiceNumber:${parseResult.invoiceNumber.effectiveValue(headerCorrections?.invoiceNumber) ?: "null"}".toByteArray())
        digest.update("invoiceDate:${parseResult.invoiceDate.effectiveValue(headerCorrections?.invoiceDate) ?: "null"}".toByteArray())
        digest.update("total:${parseResult.total.effectiveValue(headerCorrections?.total)?.toCanonicalString() ?: "null"}".toByteArray())

        // Active Lines and Matches
        // Sort matches by lineIndex to ensure determinism
        val activeLines = parseResult.lines.filter { !it.isIgnored }.sortedBy { it.index }

        activeLines.forEach { line ->
            val match = matches.find { it.lineIndex == line.index }
            digest.update("line:${line.index}".toByteArray())
            digest.update("status:${match?.status?.name ?: "null"}".toByteArray())
            digest.update("ingredient:${match?.ingredientId?.value ?: "null"}".toByteArray())
            digest.update("unitOption:${match?.unitOptionId?.value ?: "null"}".toByteArray())
            digest.update("area:${match?.inventoryAreaId?.value ?: "null"}".toByteArray())
            
            // Correction values
            val lineCorrection = line.correction
            digest.update("qty:${line.quantity.effectiveValue(lineCorrection?.quantity)?.toCanonicalString() ?: "null"}".toByteArray())
            digest.update("price:${line.unitPrice.effectiveValue(lineCorrection?.unitPrice)?.toCanonicalString() ?: "null"}".toByteArray())
            digest.update("lineTotal:${line.lineTotal.effectiveValue(lineCorrection?.lineTotal)?.toCanonicalString() ?: "null"}".toByteArray())
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun BigDecimal.toCanonicalString(): String {
        return stripTrailingZeros().toPlainString()
    }
}
