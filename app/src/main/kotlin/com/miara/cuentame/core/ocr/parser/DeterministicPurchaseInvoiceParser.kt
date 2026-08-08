package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

class DeterministicPurchaseInvoiceParser @Inject constructor() : PurchaseInvoiceParser {

    override fun parse(pages: List<OcrPageEvidence>): PurchaseInvoiceParseResult {
        val tokens = pages.flatMapIndexed { pageIdx, page ->
            if (page.widthPx <= 0 || page.heightPx <= 0) return@flatMapIndexed emptyList<LayoutToken>()
            page.blocks.flatMapIndexed { blockIdx, block ->
                block.lines.flatMapIndexed { lineIdx, line ->
                    line.elements.mapIndexed { elementIdx, element ->
                        val box = element.boundingBox
                        LayoutToken(
                            text = element.text,
                            pageIndex = pageIdx,
                            left = (box?.left?.toFloat() ?: 0f) / page.widthPx,
                            top = (box?.top?.toFloat() ?: 0f) / page.heightPx,
                            right = (box?.right?.toFloat() ?: 0f) / page.widthPx,
                            bottom = (box?.bottom?.toFloat() ?: 0f) / page.heightPx,
                            evidenceRef = OcrEvidenceRef(pageIdx, blockIdx, lineIdx, elementIdx)
                        )
                    }
                }
            }
        }

        if (tokens.isEmpty()) {
            return PurchaseInvoiceParseResult.empty()
        }

        val rows = RowClusterer.clusterIntoRows(tokens)
        val numericContext = inferNumericContext(tokens)
        
        val supplier = detectSupplier(tokens)
        val invoiceNumber = detectInvoiceNumber(tokens)
        val date = detectDate(tokens, numericContext)

        val pageLayouts = detectTableLayouts(rows)
        val lineCandidates = extractLines(rows, pageLayouts, numericContext)
        val totals = detectTotals(tokens, numericContext)

        val warnings = mutableListOf<InvoiceParseWarning>()
        if (pageLayouts.values.all { it.source == LayoutSource.Unknown }) {
            warnings.add(InvoiceParseWarning.UnknownColumnLayout)
        } else if (pageLayouts.values.any { it.source == LayoutSource.GeometricInference }) {
            warnings.add(InvoiceParseWarning.InferredColumnLayout)
        }
        
        if (numericContext.isAmbiguous) warnings.add(InvoiceParseWarning.AmbiguousNumberFormat)

        val validatedLines = lineCandidates.map { line ->
            if (line.isIgnored) return@map line
            val qty = line.quantity.normalizedValue
            val price = line.unitPrice.normalizedValue
            val total = line.lineTotal.normalizedValue
            
            if (!InvoiceMathValidator.isLineMathValid(qty, price, total)) {
                return@map line.copy(
                    warnings = line.warnings + InvoiceParseWarning.LineMathMismatch,
                    confidence = (line.confidence ?: 0.5f) * 0.8f
                )
            }
            line
        }

        if (!InvoiceMathValidator.isInvoiceMathValid(
                totals.subtotal.normalizedValue,
                totals.discount.normalizedValue,
                totals.fees.normalizedValue,
                totals.tax.normalizedValue,
                totals.total.normalizedValue
        )) {
            warnings.add(InvoiceParseWarning.InvoiceMathMismatch)
        }

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
            lines = validatedLines,
            confidence = calculateOverallConfidence(validatedLines, totals, pageLayouts),
            warnings = warnings
        )
    }

    private fun detectTableLayouts(rows: List<Row>): Map<Int, PageLayout> {
        val result = mutableMapOf<Int, PageLayout>()
        val rowsByPage = rows.groupBy { it.pageIndex }
        
        var lastValidLayout: PageLayout? = null
        
        val maxPage = rows.maxOfOrNull { it.pageIndex } ?: 0
        for (pageIdx in 0..maxPage) {
            val pageRows = rowsByPage[pageIdx] ?: emptyList()
            
            val headerRow = pageRows.find { isHeaderRow(it) }
            if (headerRow != null) {
                val layout = PageLayout.fromHeaderRow(headerRow, pageIdx)
                result[pageIdx] = layout
                lastValidLayout = layout
                continue
            }
            
            if (lastValidLayout != null && isPageCompatible(pageRows, lastValidLayout)) {
                val layout = lastValidLayout.copy(pageIndex = pageIdx, source = LayoutSource.CompatibleContinuation)
                result[pageIdx] = layout
                continue
            }
            
            val inferred = inferTableLayoutFromPage(pageRows, pageIdx, rows)
            if (inferred != null) {
                result[pageIdx] = inferred
                lastValidLayout = inferred
            } else {
                result[pageIdx] = PageLayout(pageIdx, emptyMap(), LayoutSource.Unknown)
            }
        }
        
        return result
    }

    private fun isPageCompatible(rows: List<Row>, layout: PageLayout): Boolean {
        if (rows.isEmpty()) return false
        
        val numericTokens = rows.flatMap { it.tokens }.filter { isNumeric(it.text) }
        if (numericTokens.isEmpty()) return false
        
        val totalRange = layout.columns[ColumnType.LineTotal] ?: return false
        val descRange = layout.columns[ColumnType.Description]
        
        val totalMatches = numericTokens.filter { totalRange.contains(it.centerX) }
        val distinctRowsWithTotal = totalMatches.map { t -> rows.find { it.tokens.contains(t) }?.rowId }.filterNotNull().distinct()
        
        val moneyHits = totalMatches.count { isMoneyLike(it.text) }
        
        // Strong signal: multiple rows hit the total column with money-like tokens
        if (moneyHits >= 2 && distinctRowsWithTotal.size >= 2) return true
        
        val ratio = totalMatches.size.toFloat() / numericTokens.size
        
        // Moderate signal: decent ratio and at least some description overlap
        if (ratio > 0.4f && distinctRowsWithTotal.size >= 2) {
            if (descRange == null) return true
            val descOverlap = rows.count { r -> r.tokens.any { t -> descRange.contains(t.centerX) } }
            if (descOverlap >= 2) return true
        }

        return false
    }

    private fun inferTableLayoutFromPage(pageRows: List<Row>, pageIndex: Int, allRows: List<Row>): PageLayout? {
        val candidateRows = pageRows.filter { row ->
            val numericCount = row.tokens.count { isNumeric(it.text) }
            numericCount >= 2 && row.text.length > 5 && !isHeaderRow(row)
        }
        
        if (candidateRows.isEmpty()) return null

        val supportThreshold = if (candidateRows.size >= 3) 3 else 2

        val numericTokens = candidateRows.flatMap { it.tokens }.filter { isNumeric(it.text) }
        var clusters = RowClusterer.clusterTokensByX(numericTokens, allRows)
            .filter { it.support >= supportThreshold }
            .sortedBy { it.avgLeft }

        if (clusters.isEmpty()) return null
        
        val cols = mutableMapOf<ColumnType, FloatRange>()
        
        if (clusters.first().avgLeft < 0.2f && clusters.size >= 4) {
             val leftmost = clusters.first()
             cols[ColumnType.SKU] = leftmost.toRange(0.03f)
             clusters = clusters.drop(1)
        }

        val moneyClusters = clusters.filter { c -> 
            val moneyRows = c.tokens.filter { isMoneyLike(it.text) }
                .map { t -> allRows.find { it.tokens.contains(t) }?.rowId }
                .filterNotNull()
                .distinct()
            moneyRows.size >= 2 || (candidateRows.size < 3 && moneyRows.size >= 1)
        }
        val rightmost = if (moneyClusters.isNotEmpty()) moneyClusters.last() else clusters.last()
        
        cols[ColumnType.LineTotal] = rightmost.toRange()

        if (clusters.size >= 2) {
            val remaining = clusters.filter { it != rightmost }
            if (remaining.size >= 2) {
                cols[ColumnType.UnitPrice] = remaining.last().toRange()
                cols[ColumnType.Quantity] = remaining[remaining.size - 2].toRange()
            } else {
                val second = remaining.last()
                if (isMoneyLike(second.tokens.firstOrNull()?.text ?: "")) {
                     cols[ColumnType.UnitPrice] = second.toRange()
                } else if (second.avgRight < rightmost.avgLeft - 0.1f) {
                     cols[ColumnType.Quantity] = second.toRange()
                }
            }
        }

        val firstNumericLeft = clusters.minOf { it.avgLeft }
        
        if (!cols.containsKey(ColumnType.SKU)) {
            val skuCandidateTokens = candidateRows.flatMap { it.tokens }
                .filter { it.right < firstNumericLeft && it.text.length >= 3 }
            
            val skuClusters = RowClusterer.clusterTokensByX(skuCandidateTokens, allRows)
                .filter { it.support >= 2 && it.avgLeft < 0.25f }
            
            if (skuClusters.isNotEmpty()) {
                val leftmost = skuClusters.minByOrNull { it.avgLeft }!!
                cols[ColumnType.SKU] = leftmost.toRange(0.03f)
                cols[ColumnType.Description] = FloatRange(leftmost.avgRight + 0.01f, firstNumericLeft - 0.01f)
            } else {
                cols[ColumnType.Description] = FloatRange(0.05f, firstNumericLeft - 0.01f)
            }
        } else {
            val skuRange = cols[ColumnType.SKU]!!
            cols[ColumnType.Description] = FloatRange(skuRange.end + 0.01f, firstNumericLeft - 0.01f)
        }
        
        return PageLayout(pageIndex, cols, LayoutSource.GeometricInference)
    }

    private fun extractLines(
        rows: List<Row>,
        pageLayouts: Map<Int, PageLayout>,
        context: NumericContext
    ): List<ParsedInvoiceLineCandidate> {
        val candidates = mutableListOf<ParsedInvoiceLineCandidate>()
        var lineIndex = 0

        for (i in rows.indices) {
            val row = rows[i]
            val layout = pageLayouts[row.pageIndex] ?: continue
            if (isHeaderRow(row)) continue
            
            val isPotentialLine = if (layout.source != LayoutSource.Unknown) {
                row.tokens.any { t -> 
                    val type = layout.getColumnType(t.centerX)
                    (type == ColumnType.Quantity || type == ColumnType.UnitPrice || type == ColumnType.LineTotal) && isNumeric(t.text)
                }
            } else {
                row.tokens.count { isNumeric(it.text) } >= 2 && row.tokens.any { it.text.length > 2 && !isNumeric(it.text) }
            }

            if (isPotentialLine) {
                val candidate = if (layout.source != LayoutSource.Unknown) {
                    extractLineWithLayout(row, layout, context, lineIndex++)
                } else {
                    extractLineFallback(row, context, lineIndex++)
                }
                
                val prevRow = rows.getOrNull(i - 1)
                if (prevRow != null && prevRow.pageIndex == row.pageIndex && !isHeaderRow(prevRow) && 
                    ((candidate.description.detectedText?.length ?: 0) < 20)) {
                    
                     val isPrevRowPart = prevRow.tokens.none { t -> 
                         val type = layout.getColumnType(t.centerX)
                         type == ColumnType.Quantity || type == ColumnType.UnitPrice || type == ColumnType.LineTotal
                     }
                     
                     if (isPrevRowPart) {
                         val combinedDesc = prevRow.text + " " + (candidate.description.detectedText ?: "")
                         var updatedCandidate = candidate.copy(
                             description = candidate.description.copy(
                                 detectedText = combinedDesc,
                                 normalizedValue = combinedDesc,
                                 evidenceRefs = prevRow.tokens.map { it.evidenceRef } + candidate.description.evidenceRefs
                             )
                         )
                         if (updatedCandidate.vendorCode.normalizedValue == null) {
                             val prevRowFields = mutableMapOf<ColumnType, MutableList<LayoutToken>>()
                             prevRow.tokens.forEach { t ->
                                 val type = layout.getColumnType(t.centerX)
                                 if (type != null) prevRowFields.getOrPut(type) { mutableListOf() }.add(t)
                             }
                             updatedCandidate = updatedCandidate.copy(vendorCode = extractField(prevRowFields[ColumnType.SKU]))
                         }
                         candidates.add(updatedCandidate)
                     } else {
                         candidates.add(candidate)
                     }
                } else {
                     candidates.add(candidate)
                }
            } else if (candidates.isNotEmpty() && isDescriptionContinuation(row, layout)) {
                 val last = candidates.last()
                 val rowText = row.text.trim()
                 if (rowText.isNotEmpty()) {
                     val newDesc = last.description.detectedText + " " + rowText
                     candidates[candidates.size - 1] = last.copy(
                         description = last.description.copy(
                             detectedText = newDesc,
                             normalizedValue = newDesc,
                             evidenceRefs = last.description.evidenceRefs + row.tokens.map { it.evidenceRef }
                         )
                     )
                 }
            }
        }
        return candidates
    }

    private fun isHeaderRow(row: Row): Boolean {
        if (row.tokens.count { isNumeric(it.text) } >= 3) return false
        return scoreHeaderRow(row) > 1.5
    }

    private fun scoreHeaderRow(row: Row): Double {
        var score = 0.0
        val text = row.text.lowercase()
        if (text.contains(Regex("desc|item|qty|cant|price|precio|total|amount|importe"))) score += 1.0
        if (HeaderAlias.QTY.any { text.contains(it) }) score += 0.5
        if (HeaderAlias.PRICE.any { text.contains(it) }) score += 0.5
        if (HeaderAlias.AMOUNT.any { text.contains(it) }) score += 0.5
        return score
    }

    private fun isDescriptionContinuation(row: Row, layout: PageLayout): Boolean {
        if (row.tokens.any { isNumeric(it.text) }) return false
        
        val text = row.text.lowercase()
        if (text.contains(Regex("thank you|gracias|payment|remit|due date|balance|total|subtotal|tax"))) return false

        if (layout.source == LayoutSource.Unknown) return row.text.length > 5 && row.tokens.all { it.left > 0.1f }
        
        val descRange = layout.columns[ColumnType.Description] ?: return false
        val overlap = row.tokens.count { t -> descRange.contains(t.centerX) }
        return overlap.toFloat() / row.tokens.size > 0.6f
    }

    private fun extractLineWithLayout(row: Row, layout: PageLayout, context: NumericContext, index: Int): ParsedInvoiceLineCandidate {
        val fields = mutableMapOf<ColumnType, MutableList<LayoutToken>>()
        row.tokens.forEach { t ->
            val type = layout.getColumnType(t.centerX)
            if (type != null) {
                fields.getOrPut(type) { mutableListOf() }.add(t)
            }
        }

        val descTokens = fields[ColumnType.Description] ?: emptyList()
        val rawDescription = extractField(descTokens)
        
        // Try individual tokens first
        val packageFromDesc = PackageTextDetector.findPackageToken(row.tokens.map { it.text })
            ?: PackageTextDetector.detectPackageText(rawDescription.detectedText ?: "")
        
        val packageField: ParsedField<String?> = if (packageFromDesc != null) {
            ParsedField(packageFromDesc, packageFromDesc, 0.8f, row.tokens.filter { it.text.contains(packageFromDesc) }.map { it.evidenceRef }.ifEmpty { rawDescription.evidenceRefs })
        } else {
            extractField(fields[ColumnType.Package])
        }
        
        val finalDescription = if (packageFromDesc != null) {
            val cleaned = rawDescription.detectedText?.replace(packageFromDesc, "")?.trim()
            rawDescription.copy(detectedText = cleaned, normalizedValue = cleaned)
        } else {
            rawDescription
        }

        val confidence = when(layout.source) {
            LayoutSource.SemanticHeader -> 0.9f
            LayoutSource.CompatibleContinuation -> 0.85f
            LayoutSource.GeometricInference -> 0.7f
            else -> 0.5f
        }

        return ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = extractField(fields[ColumnType.SKU]),
            description = finalDescription,
            quantity = extractNumericField(fields[ColumnType.Quantity], context),
            packageText = packageField,
            unitPrice = extractNumericField(fields[ColumnType.UnitPrice], context),
            lineTotal = extractNumericField(fields[ColumnType.LineTotal], context),
            confidence = confidence,
            evidenceRefs = row.tokens.map { it.evidenceRef }
        )
    }

    private fun extractLineFallback(row: Row, context: NumericContext, index: Int): ParsedInvoiceLineCandidate {
        val numericTokens = row.tokens.filter { isNumeric(it.text) }
        val textTokens = row.tokens.filter { !isNumeric(it.text) }
        
        val skuCandidate = row.tokens.firstOrNull { it.text.length >= 3 && it.left < 0.2f }
        
        return ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = if (skuCandidate != null && (skuCandidate in numericTokens || skuCandidate.text.any { it.isDigit() })) 
                extractField(listOf(skuCandidate)) else ParsedField(null, null, 0f),
            description = extractField(textTokens.filter { it != skuCandidate }),
            quantity = if (numericTokens.size >= 3) extractNumericField(listOf(numericTokens[0]), context) else ParsedField(null, null, 0f),
            packageText = ParsedField(null, null, 0f),
            unitPrice = if (numericTokens.size >= 3) extractNumericField(listOf(numericTokens[1]), context) else ParsedField(null, null, 0f),
            lineTotal = extractNumericField(listOf(numericTokens.last()), context),
            confidence = 0.4f,
            evidenceRefs = row.tokens.map { it.evidenceRef }
        )
    }

    private fun extractField(tokens: List<LayoutToken>?): ParsedField<String?> {
        if (tokens.isNullOrEmpty()) return ParsedField(null, null, 0f)
        val text = tokens.joinToString(" ") { it.text }
        return ParsedField(text, text, 0.9f, tokens.map { it.evidenceRef })
    }

    private fun extractNumericField(tokens: List<LayoutToken>?, context: NumericContext): ParsedField<BigDecimal?> {
        if (tokens.isNullOrEmpty()) return ParsedField(null, null, 0f)
        val text = tokens.joinToString("") { it.text }
        val value = parseBigDecimal(text, context)
        return ParsedField(text, value, if (value != null) 0.9f else 0.1f, tokens.map { it.evidenceRef })
    }

    private fun isNumeric(text: String): Boolean {
        val sanitized = text.replace(Regex("[^0-9]"), "")
        if (sanitized.isEmpty()) return false
        val letters = text.count { it.isLetter() }
        if (letters > text.length / 2 && text.length > 3) return false
        val totalMeaningful = text.replace(Regex("[^0-9,.()\\- $]"), "").length
        return totalMeaningful >= 1
    }
    
    private fun isMoneyLike(text: String): Boolean {
        return text.contains(Regex("[.,][0-9]{2}")) || text.contains("$")
    }

    private fun detectSupplier(tokens: List<LayoutToken>): ParsedField<String?> {
        val firstPage = tokens.filter { it.pageIndex == 0 }
        val candidates = firstPage.filter { it.top < 0.3f && !isNumeric(it.text) && it.text.length > 2 }
            .sortedBy { it.top }

        if (candidates.isEmpty()) return ParsedField(null, null, 0f)

        val best = candidates.firstOrNull { t ->
            val lowText = t.text.lowercase()
            !lowText.contains(Regex("invoice|factura|date|fecha|page|página|phone|tel|email|web"))
        } ?: candidates.first()

        val rowTokens = firstPage.filter { abs(it.top - best.top) < 0.005f }.sortedBy { it.left }
        val name = rowTokens.joinToString(" ") { it.text }

        return ParsedField(name, name, 0.7f, rowTokens.map { it.evidenceRef })
    }

    private fun detectInvoiceNumber(tokens: List<LayoutToken>): ParsedField<String?> {
        val labels = listOf("invoice", "factura", "number", "número", "num", "no.", "inv#", "remisión")
        for (token in tokens) {
            if (labels.any { token.text.lowercase().contains(it) }) {
                val nearby = tokens.filter {
                    it.pageIndex == token.pageIndex &&
                            abs(it.top - token.top) < 0.015f &&
                            it.left > token.left && it.left < token.left + 0.4f
                }.sortedBy { it.left }

                val value = nearby.firstOrNull { it.text.any { c -> c.isDigit() } && it.text.length >= 3 }
                if (value != null) {
                    return ParsedField(value.text, value.text, 0.85f, listOf(token.evidenceRef, value.evidenceRef))
                }
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun detectDate(tokens: List<LayoutToken>, context: NumericContext): ParsedField<LocalDate?> {
        val datePattern = Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}")

        for (token in tokens) {
            val match = datePattern.find(token.text)
            if (match != null) {
                val normalized = parseLocalDate(match.value)
                if (normalized != null) {
                    return ParsedField(token.text, normalized, 0.8f, listOf(token.evidenceRef))
                }
            }
            
            if (token.text.lowercase().contains(" de ")) {
                 val normalized = parseLocalDate(token.text)
                 if (normalized != null) {
                     return ParsedField(token.text, normalized, 0.8f, listOf(token.evidenceRef))
                 }
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun parseLocalDate(text: String): LocalDate? {
        val formats = listOf(
            "MM/dd/yyyy", "M/d/yyyy", "MM/dd/yy", "M/d/yy",
            "dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {
            }
        }

        val spanishDate = text.lowercase()
        if (spanishDate.contains(" de ")) {
            val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
            val parts = spanishDate.split(" de ")
            if (parts.size >= 3) {
                val day = parts[0].filter { it.isDigit() }.toIntOrNull()
                val month = months.indexOf(parts[1].trim()) + 1
                val year = parts[2].filter { it.isDigit() }.toIntOrNull()
                if (day != null && month > 0 && year != null) {
                    return LocalDate.of(if (year < 100) 2000 + year else year, month, day)
                }
            }
        }

        return null
    }

    private fun detectTotals(tokens: List<LayoutToken>, context: NumericContext): TotalsResult {
        val totalLabels = listOf("\\btotal\\b", "importe total", "grand total")
        val taxLabels = listOf("tax", "iva", "itbis", "impuesto")
        val subtotalLabels = listOf("subtotal", "sub-total", "sub total")
        val discountLabels = listOf("discount", "descuento")
        val feesLabels = listOf("fee", "cargo", "flete", "shipping", "delivery")

        return TotalsResult(
            subtotal = findAmountNearLabel(tokens, subtotalLabels, context),
            discount = findAmountNearLabel(tokens, discountLabels, context),
            fees = findAmountNearLabel(tokens, feesLabels, context),
            tax = findAmountNearLabel(tokens, taxLabels, context),
            total = findAmountNearLabel(tokens, totalLabels, context, isRegex = true),
            currency = ParsedField(null, null, 0f)
        )
    }

    private fun findAmountNearLabel(
        tokens: List<LayoutToken>, 
        labels: List<String>, 
        context: NumericContext,
        isRegex: Boolean = false
    ): ParsedField<BigDecimal?> {
        for (token in tokens) {
            val lowText = token.text.lowercase()
            val matched = if (isRegex) {
                labels.any { Regex(it).containsMatchIn(lowText) }
            } else {
                labels.any { lowText.contains(it) }
            }
            
            if (matched) {
                val selfValue = parseBigDecimal(token.text, context)
                if (selfValue != null && isMoneyLike(token.text)) {
                    return ParsedField(token.text, selfValue, 0.8f, listOf(token.evidenceRef))
                }

                val nearby = tokens.filter {
                    it.pageIndex == token.pageIndex &&
                            abs(it.top - token.top) < 0.015f &&
                            it.left > token.left
                }.sortedBy { it.left }

                val valueToken = nearby.firstOrNull { isNumeric(it.text) }
                if (valueToken != null) {
                    val value = parseBigDecimal(valueToken.text, context)
                    return ParsedField(valueToken.text, value, if (value != null) 0.9f else 0.1f, listOf(token.evidenceRef, valueToken.evidenceRef))
                }
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun inferNumericContext(tokens: List<LayoutToken>): NumericContext {
        var commaAsDecimal = 0
        var dotAsDecimal = 0
        
        val moneyRegex = Regex("[0-9]{1,3}([,.][0-9]{3})*[,.][0-9]{2}")
        
        for (token in tokens) {
            val match = moneyRegex.find(token.text) ?: continue
            val value = match.value
            val lastComma = value.lastIndexOf(',')
            val lastDot = value.lastIndexOf('.')
            
            if (lastComma > lastDot) commaAsDecimal++
            if (lastDot > lastComma) dotAsDecimal++
        }
        
        return when {
            commaAsDecimal > dotAsDecimal -> NumericContext(',', false)
            dotAsDecimal > commaAsDecimal -> NumericContext('.', false)
            else -> NumericContext('.', true)
        }
    }

    private fun parseBigDecimal(text: String?, context: NumericContext): BigDecimal? {
        if (text == null) return null
        
        val moneyRegex = Regex("-?[0-9]{1,3}([,.][0-9]{3})*[,.][0-9]{2}")
        val matches = moneyRegex.findAll(text).toList()
        val matchCandidate = matches.lastOrNull()?.value
        
        val sanitized = matchCandidate ?: text.replace(Regex("[^0-9,.-]"), "")
        if (sanitized.isBlank() || sanitized == "." || sanitized == "-") return null
        
        return try {
            val normalized = if (context.decimalSeparator == ',') {
                sanitized.replace(".", "").replace(",", ".")
            } else {
                sanitized.replace(",", "")
            }
            val final = if (text.contains("(") && text.contains(")")) "-$normalized" else normalized
            BigDecimal(final)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateOverallConfidence(
        lines: List<ParsedInvoiceLineCandidate>, 
        totals: TotalsResult, 
        layouts: Map<Int, PageLayout>
    ): Float {
        var score = 0.0f
        val layoutQual = layouts.values.map { 
            when(it.source) {
                LayoutSource.SemanticHeader -> 1.0f
                LayoutSource.CompatibleContinuation -> 0.8f
                LayoutSource.GeometricInference -> 0.5f
                else -> 0.0f
            }
        }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
        
        score += layoutQual * 0.4f
        if (totals.total.normalizedValue != null) score += 0.2f
        if (lines.isNotEmpty()) {
            val avgLineConf = lines.map { it.confidence ?: 0f }.sum() / lines.size
            score += avgLineConf * 0.4f
        }
        return score.coerceIn(0f, 1f)
    }

    private data class PageLayout(
        val pageIndex: Int,
        val columns: Map<ColumnType, FloatRange>,
        val source: LayoutSource
    ) {
        fun getColumnType(x: Float): ColumnType? {
            return columns[ColumnType.LineTotal]?.let { if (it.contains(x)) ColumnType.LineTotal else null }
                ?: columns[ColumnType.UnitPrice]?.let { if (it.contains(x)) ColumnType.UnitPrice else null }
                ?: columns[ColumnType.Quantity]?.let { if (it.contains(x)) ColumnType.Quantity else null }
                ?: columns[ColumnType.Package]?.let { if (it.contains(x)) ColumnType.Package else null }
                ?: columns[ColumnType.SKU]?.let { if (it.contains(x)) ColumnType.SKU else null }
                ?: columns[ColumnType.Description]?.let { if (it.contains(x)) ColumnType.Description else null }
        }

        companion object {
            fun fromHeaderRow(row: Row, pageIndex: Int): PageLayout {
                val cols = mutableMapOf<ColumnType, FloatRange>()
                val sortedTokens = row.tokens.sortedBy { it.left }
                
                for (i in sortedTokens.indices) {
                    val token = sortedTokens[i]
                    val nextToken = sortedTokens.getOrNull(i + 1)
                    val text = token.text.lowercase()
                    
                    val type = when {
                        HeaderAlias.SKU.any { text.contains(it) } -> ColumnType.SKU
                        HeaderAlias.QTY.any { text.contains(it) } -> ColumnType.Quantity
                        HeaderAlias.PACK.any { text.contains(it) } -> ColumnType.Package
                        HeaderAlias.PRICE.any { text.contains(it) } -> ColumnType.UnitPrice
                        HeaderAlias.AMOUNT.any { text.contains(it) } -> ColumnType.LineTotal
                        HeaderAlias.DESCRIPTION.any { text.contains(it) } -> ColumnType.Description
                        else -> null
                    }
                    
                    if (type != null && !cols.containsKey(type)) {
                        val start = token.left
                        val end = nextToken?.left ?: 1.0f
                        cols[type] = FloatRange(start, end)
                    }
                }
                return PageLayout(pageIndex, cols, LayoutSource.SemanticHeader)
            }
        }
    }

    private enum class LayoutSource {
        SemanticHeader, CompatibleContinuation, GeometricInference, Unknown
    }

    private enum class ColumnType {
        SKU, Description, Quantity, Package, UnitPrice, LineTotal
    }

    private data class NumericContext(
        val decimalSeparator: Char,
        val isAmbiguous: Boolean
    )

    private object HeaderAlias {
        val QTY = listOf("qty", "quantity", "cant", "cantidad", "unidades")
        val PRICE = listOf("price", "unit price", "precio", "cost", "unit cost")
        val AMOUNT = listOf("amount", "total", "importe", "ext", "extension")
        val SKU = listOf("item", "code", "sku", "código", "articulo", "artículo")
        val DESCRIPTION = listOf("description", "descripción", "desc")
        val PACK = listOf("pack", "size", "empaque", "paquete")
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

private fun PurchaseInvoiceParseResult.Companion.empty() = PurchaseInvoiceParseResult(
    supplierNameCandidate = ParsedField(null, null, 0f),
    invoiceNumber = ParsedField(null, null, 0f),
    invoiceDate = ParsedField(null, null, 0f),
    subtotal = ParsedField(null, null, 0f),
    discount = ParsedField(null, null, 0f),
    fees = ParsedField(null, null, 0f),
    tax = ParsedField(null, null, 0f),
    total = ParsedField(null, null, 0f),
    currency = ParsedField(null, null, 0f),
    lines = emptyList(),
    confidence = 0f,
    warnings = emptyList()
)
