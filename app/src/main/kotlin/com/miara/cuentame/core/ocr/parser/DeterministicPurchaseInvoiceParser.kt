package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.OcrElementEvidence
import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class DeterministicPurchaseInvoiceParser @Inject constructor() : PurchaseInvoiceParser {

    override fun parse(pages: List<OcrPageEvidence>): PurchaseInvoiceParseResult {
        val allElements = pages.flatMapIndexed { pageIdx, page ->
            page.blocks.flatMapIndexed { blockIdx, block ->
                block.lines.flatMapIndexed { lineIdx, line ->
                    line.elements.mapIndexed { elementIdx, element ->
                        ElementWithMeta(
                            element = element,
                            pageIndex = pageIdx,
                            blockIndex = blockIdx,
                            lineIndex = lineIdx,
                            elementIndex = elementIdx,
                            pageWidth = page.widthPx,
                            pageHeight = page.heightPx
                        )
                    }
                }
            }
        }

        // Basic heuristic-based parsing
        val supplier = detectSupplier(allElements)
        val invoiceNumber = detectInvoiceNumber(allElements)
        val date = detectDate(allElements)
        
        val rows = clusterIntoRows(allElements)
        val lineCandidates = parseLines(rows)

        val totals = detectTotals(allElements)

        return PurchaseInvoiceParseResult(
            supplierNameCandidate = supplier,
            invoiceNumber = invoiceNumber,
            invoiceDate = date,
            subtotal = totals.subtotal,
            discount = totals.discount,
            fees = totals.fees,
            tax = totals.tax,
            total = totals.total,
            currency = totals.currency,
            lines = lineCandidates,
            confidence = calculateOverallConfidence(lineCandidates, totals),
            warnings = emptyList()
        )
    }

    private fun detectSupplier(elements: List<ElementWithMeta>): ParsedField<String?> {
        // Heuristic: Top 20% of first page, largest font or first meaningful text
        val firstPageElements = elements.filter { it.pageIndex == 0 }
        val topElements = firstPageElements.filter { it.yProgress < 0.2f }
            .sortedBy { it.element.boundingBox?.top ?: 0 }
        
        val firstElement = topElements.firstOrNull { 
            val text = it.element.text
            text.length >= 3 && !isNumeric(text) && !isCommonLabel(text) 
        } ?: return ParsedField(null, null, 0f)

        // Find other elements on the same line
        val sameLine = firstPageElements.filter {
            it.blockIndex == firstElement.blockIndex &&
            it.lineIndex == firstElement.lineIndex
        }.sortedBy { it.elementIndex }

        val fullText = sameLine.joinToString(" ") { it.element.text }

        return ParsedField(
            detectedText = fullText,
            normalizedValue = fullText,
            confidence = 0.7f,
            evidenceRefs = sameLine.map { it.toRef() }
        )
    }

    private fun detectInvoiceNumber(elements: List<ElementWithMeta>): ParsedField<String?> {
        val labels = listOf("invoice", "factura", "number", "número", "num", "no.", "inv#")
        for (i in elements.indices) {
            val element = elements[i]
            if (labels.any { element.element.text.lowercase().contains(it) }) {
                // Check following elements on same line or nearby
                val nearby = elements.filter { 
                    it.pageIndex == element.pageIndex && 
                    Math.abs(it.yProgress - element.yProgress) < 0.02f &&
                    it.xProgress > element.xProgress &&
                    it.xProgress < element.xProgress + 0.3f
                }.sortedBy { it.xProgress }
                
                val value = nearby.firstOrNull { isAlphaNumericId(it.element.text) }
                if (value != null) {
                    return ParsedField(
                        detectedText = value.element.text,
                        normalizedValue = value.element.text,
                        confidence = 0.85f,
                        evidenceRefs = listOf(element.toRef(), value.toRef())
                    )
                }
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun detectDate(elements: List<ElementWithMeta>): ParsedField<LocalDate?> {
        // Simple regex for dates
        val datePattern = Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}")
        val candidate = elements.firstOrNull { datePattern.containsMatchIn(it.element.text) }
        
        val normalized = candidate?.let { parseLocalDate(it.element.text) }

        return ParsedField(
            detectedText = candidate?.element?.text,
            normalizedValue = normalized,
            confidence = if (normalized != null) 0.8f else 0f,
            evidenceRefs = candidate?.let { listOf(it.toRef()) } ?: emptyList()
        )
    }

    private fun detectTotals(elements: List<ElementWithMeta>): TotalsResult {
        val totalLabels = listOf("total", "importe total", "grand total")
        val taxLabels = listOf("tax", "iva", "itbis", "impuesto")
        val subtotalLabels = listOf("subtotal", "sub-total", "sub total")

        return TotalsResult(
            total = findAmountNearLabel(elements, totalLabels),
            tax = findAmountNearLabel(elements, taxLabels),
            subtotal = findAmountNearLabel(elements, subtotalLabels),
            discount = ParsedField(null, null, 0f),
            fees = ParsedField(null, null, 0f),
            currency = ParsedField(null, null, 0f)
        )
    }

    private fun findAmountNearLabel(elements: List<ElementWithMeta>, labels: List<String>): ParsedField<BigDecimal?> {
        for (element in elements) {
            if (labels.any { element.element.text.lowercase() == it }) {
                val nearby = elements.filter { 
                    it.pageIndex == element.pageIndex && 
                    Math.abs(it.yProgress - element.yProgress) < 0.02f &&
                    it.xProgress > element.xProgress
                }.sortedBy { it.xProgress }
                
                val value = nearby.firstOrNull { isMonetary(it.element.text) }
                if (value != null) {
                    return ParsedField(
                        detectedText = value.element.text,
                        normalizedValue = parseBigDecimal(value.element.text),
                        confidence = 0.9f,
                        evidenceRefs = listOf(element.toRef(), value.toRef())
                    )
                }
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun clusterIntoRows(elements: List<ElementWithMeta>): List<List<ElementWithMeta>> {
        return elements.groupBy { "${it.pageIndex}_${(it.yProgress * 1000).toInt() / 10}" } // 1% tolerance
            .values.map { it.sortedBy { e -> e.xProgress } }
            .sortedBy { it.first().pageIndex * 1000000 + it.first().element.boundingBox?.top!! }
    }

    private fun parseLines(rows: List<List<ElementWithMeta>>): List<ParsedInvoiceLineCandidate> {
        // Very basic line parser: look for rows with at least 3 numeric-looking elements (qty, price, amount)
        val candidates = mutableListOf<ParsedInvoiceLineCandidate>()
        var index = 0
        for (row in rows) {
            val numericElements = row.filter { isNumeric(it.element.text) }
            if (numericElements.size >= 2) {
                val description = row.filter { !isNumeric(it.element.text) && it.element.text.length > 3 }
                    .joinToString(" ") { it.element.text }
                
                if (description.isNotBlank()) {
                    candidates.add(
                        ParsedInvoiceLineCandidate(
                            index = index++,
                            vendorCode = ParsedField(null, null, 0f),
                            description = ParsedField(description, description, 0.6f),
                            quantity = ParsedField(null, null, 0f), // To be refined with column logic
                            packageText = ParsedField(null, null, 0f),
                            unitPrice = ParsedField(null, null, 0f),
                            lineTotal = ParsedField(null, null, 0f),
                            confidence = 0.5f
                        )
                    )
                }
            }
        }
        return candidates
    }

    private fun calculateOverallConfidence(lines: List<ParsedInvoiceLineCandidate>, totals: TotalsResult): Float {
        return 0.5f // Placeholder
    }

    private fun isNumeric(text: String): Boolean {
        return text.replace(",", "").replace(".", "").all { it.isDigit() }
    }

    private fun isMonetary(text: String): Boolean {
        val sanitized = text.replace("$", "").replace(",", "").replace(".", "").trim()
        return sanitized.all { it.isDigit() } && text.any { it.isDigit() }
    }

    private fun isAlphaNumericId(text: String): Boolean {
        return text.length >= 3 && text.any { it.isDigit() }
    }

    private fun isCommonLabel(text: String): Boolean {
        val labels = listOf("invoice", "date", "page", "total", "subtotal", "tax")
        return labels.any { text.lowercase().contains(it) }
    }

    private fun parseBigDecimal(text: String?): BigDecimal? {
        if (text == null) return null
        val sanitized = text.replace(Regex("[^0-9,.-]"), "")
        return try {
            // Handle comma as decimal separator if there's only one comma and no dot, or if it's the last one
            val finalString = if (sanitized.contains(",") && !sanitized.contains(".")) {
                sanitized.replace(",", ".")
            } else if (sanitized.contains(",") && sanitized.contains(".")) {
                if (sanitized.lastIndexOf(",") > sanitized.lastIndexOf(".")) {
                     sanitized.replace(".", "").replace(",", ".")
                } else {
                     sanitized.replace(",", "")
                }
            } else {
                sanitized
            }
            BigDecimal(finalString)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLocalDate(text: String): LocalDate? {
        val formats = listOf("MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "MM-dd-yyyy", "dd-MM-yyyy")
        for (format in formats) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {}
        }
        return null
    }

    private data class ElementWithMeta(
        val element: OcrElementEvidence,
        val pageIndex: Int,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
        val pageWidth: Int,
        val pageHeight: Int
    ) {
        val xProgress: Float = (element.boundingBox?.left?.toFloat() ?: 0f) / pageWidth
        val yProgress: Float = (element.boundingBox?.top?.toFloat() ?: 0f) / pageHeight
        
        fun toRef() = OcrEvidenceRef(pageIndex, blockIndex, lineIndex, elementIndex)
    }

    private data class TotalsResult(
        val subtotal: ParsedField<BigDecimal?>,
        val discount: ParsedField<BigDecimal?>,
        val fees: ParsedField<BigDecimal?>,
        val tax: ParsedField<BigDecimal?>,
        val total: ParsedField<BigDecimal?>,
        val currency: ParsedField<String?>
    )
}
