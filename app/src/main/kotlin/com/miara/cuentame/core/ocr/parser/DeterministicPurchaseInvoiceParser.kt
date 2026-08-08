package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.common.decimal.MoneyComparison
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

        val rows = clusterIntoRows(tokens)
        val numericContext = inferNumericContext(tokens)
        
        // 1. Header identification
        val supplier = detectSupplier(tokens)
        val invoiceNumber = detectInvoiceNumber(tokens)
        val date = detectDate(tokens, numericContext)

        // 2. Table identification
        val tableLayout = detectTableLayout(rows)
        
        // 3. Line extraction
        val lineCandidates = extractLines(rows, tableLayout, numericContext)

        // 4. Totals identification
        val totals = detectTotals(tokens, numericContext)

        val warnings = mutableListOf<InvoiceParseWarning>()
        if (tableLayout == null) {
            warnings.add(InvoiceParseWarning.UnknownColumnLayout)
        } else if (tableLayout.isInferred) {
            warnings.add(InvoiceParseWarning.InferredColumnLayout)
        }
        
        if (numericContext.isAmbiguous) warnings.add(InvoiceParseWarning.AmbiguousNumberFormat)

        // Line math validation
        val validatedLines = lineCandidates.map { line ->
            if (line.isIgnored) return@map line
            val qty = line.quantity.normalizedValue
            val price = line.unitPrice.normalizedValue
            val total = line.lineTotal.normalizedValue
            
            if (qty != null && price != null && total != null) {
                val expected = qty.multiply(price).setScale(2, RoundingMode.HALF_UP)
                if (!MoneyComparison.moneyApproximatelyEquals(expected, total)) {
                    return@map line.copy(
                        warnings = line.warnings + InvoiceParseWarning.LineMathMismatch,
                        confidence = (line.confidence ?: 0.5f) * 0.8f
                    )
                }
            }
            line
        }

        // Invoice math validation
        val subtotal = totals.subtotal.normalizedValue
        val tax = totals.tax.normalizedValue
        val totalAmount = totals.total.normalizedValue
        if (subtotal != null && totalAmount != null) {
             val expected = subtotal.add(tax ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
             if (!MoneyComparison.moneyApproximatelyEquals(expected, totalAmount)) {
                 warnings.add(InvoiceParseWarning.InvoiceMathMismatch)
             }
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
            confidence = calculateOverallConfidence(validatedLines, totals, tableLayout != null),
            warnings = warnings
        )
    }

    private fun clusterIntoRows(tokens: List<LayoutToken>): List<Row> {
        val rows = mutableListOf<Row>()
        tokens.groupBy { it.pageIndex }.forEach { (_, pageTokens) ->
            val sorted = pageTokens.sortedBy { it.top }
            var currentRowTokens = mutableListOf<LayoutToken>()
            
            for (token in sorted) {
                if (currentRowTokens.isEmpty()) {
                    currentRowTokens.add(token)
                } else {
                    val rowTop = currentRowTokens.minOf { it.top }
                    val rowBottom = currentRowTokens.maxOf { it.bottom }
                    val rowHeight = rowBottom - rowTop
                    
                    val verticalOverlap = Math.min(token.bottom, rowBottom) - Math.max(token.top, rowTop)
                    val overlapRatio = if (rowHeight > 0) verticalOverlap / rowHeight else 0f
                    
                    // Same row if substantial vertical overlap or centers are very close
                    val sameRow = overlapRatio > 0.5f || Math.abs((token.top + token.bottom)/2 - (rowTop + rowBottom)/2) < 0.005f
                    
                    if (sameRow) {
                        currentRowTokens.add(token)
                    } else {
                        rows.add(Row(currentRowTokens.sortedBy { it.left }))
                        currentRowTokens = mutableListOf(token)
                    }
                }
            }
            if (currentRowTokens.isNotEmpty()) {
                rows.add(Row(currentRowTokens.sortedBy { it.left }))
            }
        }
        return rows.sortedWith(compareBy({ it.pageIndex }, { it.top }))
    }

    private fun detectTableLayout(rows: List<Row>): TableLayout? {
        // 1. Look for semantic headers across all pages
        val headerCandidates = rows.filter { isHeaderRow(it) }
            .sortedByDescending { scoreHeaderRow(it) }
        
        val bestHeader = headerCandidates.firstOrNull()
        if (bestHeader != null) {
            return TableLayout.fromHeaderRow(bestHeader)
        }

        // 2. Geometric fallback across all pages
        return inferTableLayoutFromData(rows)
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

    private fun inferTableLayoutFromData(rows: List<Row>): TableLayout? {
        // Filter rows that look like product lines (have multiple numeric tokens and some text)
        val candidateRows = rows.filter { row ->
            val numericCount = row.tokens.count { isNumeric(it.text) }
            numericCount >= 2 && row.text.length > 10 && !isHeaderRow(row)
        }
        
        if (candidateRows.size < 2) return null // Need at least 2 rows for recurring geometry

        // Identify recurring X clusters for numeric tokens
        val numericTokens = candidateRows.flatMap { it.tokens }.filter { isNumeric(it.text) }
        val clusters = clusterTokensByX(numericTokens)
            .filter { it.support >= 2 }
            .sortedBy { it.avgLeft }

        if (clusters.isEmpty()) return null

        val cols = mutableMapOf<ColumnType, FloatRange>()
        
        // Heuristic: Rightmost is Line Total
        val rightmost = clusters.last()
        cols[ColumnType.LineTotal] = rightmost.toRange()

        // If we have more clusters, try to assign UnitPrice and Quantity
        if (clusters.size >= 2) {
            val secondRightmost = clusters[clusters.size - 2]
            if (clusters.size >= 3) {
                cols[ColumnType.UnitPrice] = secondRightmost.toRange()
                cols[ColumnType.Quantity] = clusters[clusters.size - 3].toRange()
            } else {
                // With only two numeric columns, it's ambiguous. 
                // Often Qty and Total, or Price and Total.
                // We'll map to UnitPrice for now if it's close to total, otherwise Qty.
                if (rightmost.avgLeft - secondRightmost.avgRight < 0.15f) {
                    cols[ColumnType.UnitPrice] = secondRightmost.toRange()
                } else {
                    cols[ColumnType.Quantity] = secondRightmost.toRange()
                }
            }
        }

        // Description is usually to the left of the numeric columns
        val firstNumericLeft = clusters.minOf { it.avgLeft }
        cols[ColumnType.Description] = FloatRange(0.1f, firstNumericLeft - 0.01f)
        
        // SKU might be leftmost
        val skuCandidateTokens = candidateRows.flatMap { it.tokens }
            .filter { it.right < firstNumericLeft && it.text.length >= 3 }
        
        val skuClusters = clusterTokensByX(skuCandidateTokens)
            .filter { it.support >= 2 && it.avgLeft < 0.2f }
        
        if (skuClusters.isNotEmpty()) {
            val leftmost = skuClusters.minByOrNull { it.avgLeft }!!
            cols[ColumnType.SKU] = leftmost.toRange()
            // Adjust description to start after SKU
            cols[ColumnType.Description] = FloatRange(leftmost.avgRight + 0.01f, firstNumericLeft - 0.01f)
        }

        return TableLayout(cols, isInferred = true)
    }

    private data class TokenCluster(
        val tokens: List<LayoutToken>,
        val avgLeft: Float,
        val avgRight: Float,
        val support: Int
    ) {
        fun toRange() = FloatRange(avgLeft - 0.02f, avgRight + 0.02f)
    }

    private fun clusterTokensByX(tokens: List<LayoutToken>): List<TokenCluster> {
        if (tokens.isEmpty()) return emptyList()
        val sorted = tokens.sortedBy { it.left }
        val clusters = mutableListOf<MutableList<LayoutToken>>()
        
        for (token in sorted) {
            val matchedCluster = clusters.find { cluster ->
                val avgLeft = cluster.map { it.left }.average().toFloat()
                Math.abs(token.left - avgLeft) < 0.03f
            }
            if (matchedCluster != null) {
                matchedCluster.add(token)
            } else {
                clusters.add(mutableListOf(token))
            }
        }
        
        return clusters.map { c ->
            TokenCluster(
                tokens = c,
                avgLeft = c.map { it.left }.average().toFloat(),
                avgRight = c.map { it.right }.average().toFloat(),
                support = c.size
            )
        }
    }

    private fun extractLines(
        rows: List<Row>,
        layout: TableLayout?,
        context: NumericContext
    ): List<ParsedInvoiceLineCandidate> {
        val candidates = mutableListOf<ParsedInvoiceLineCandidate>()
        var lineIndex = 0

        for (i in rows.indices) {
            val row = rows[i]
            if (isHeaderRow(row)) continue
            
            val isPotentialLine = if (layout != null) {
                row.tokens.any { t -> 
                    val type = layout.getColumnType(t.left + (t.right - t.left)/2)
                    (type == ColumnType.Quantity || type == ColumnType.UnitPrice || type == ColumnType.LineTotal) && isNumeric(t.text)
                }
            } else {
                row.tokens.count { isNumeric(it.text) } >= 2 && row.tokens.any { it.text.length > 2 && !isNumeric(it.text) }
            }

            if (isPotentialLine) {
                val candidate = if (layout != null) {
                    extractLineWithLayout(row, layout, context, lineIndex++)
                } else {
                    extractLineFallback(row, context, lineIndex++)
                }
                candidates.add(candidate)
            } else if (candidates.isNotEmpty() && isDescriptionContinuation(row, layout)) {
                 val last = candidates.last()
                 val newDesc = last.description.detectedText + " " + row.text
                 candidates[candidates.size - 1] = last.copy(
                     description = last.description.copy(
                         detectedText = newDesc,
                         normalizedValue = newDesc,
                         evidenceRefs = last.description.evidenceRefs + row.tokens.map { it.evidenceRef }
                     )
                 )
            }
        }
        return candidates
    }

    private fun isHeaderRow(row: Row): Boolean {
        if (row.tokens.count { isNumeric(it.text) } >= 3) return false // Too many numbers for a header
        return scoreHeaderRow(row) > 1.5
    }

    private fun isDescriptionContinuation(row: Row, layout: TableLayout?): Boolean {
        if (row.tokens.any { isNumeric(it.text) }) return false
        if (layout == null) return row.text.length > 5 && row.tokens.all { it.left > 0.1f }
        
        val descRange = layout.columns[ColumnType.Description] ?: return false
        // Continuation should mostly align with the description column
        val overlap = row.tokens.count { t -> descRange.contains(t.left + (t.right - t.left) / 2) }
        return overlap.toFloat() / row.tokens.size > 0.7f
    }

    private fun extractLineWithLayout(row: Row, layout: TableLayout, context: NumericContext, index: Int): ParsedInvoiceLineCandidate {
        val fields = mutableMapOf<ColumnType, MutableList<LayoutToken>>()
        row.tokens.forEach { t ->
            val type = layout.getColumnType(t.left + (t.right - t.left)/2)
            if (type != null) {
                fields.getOrPut(type) { mutableListOf() }.add(t)
            }
        }

        return ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = extractField(fields[ColumnType.SKU]),
            description = extractField(fields[ColumnType.Description]),
            quantity = extractNumericField(fields[ColumnType.Quantity], context),
            packageText = extractField(fields[ColumnType.Package]),
            unitPrice = extractNumericField(fields[ColumnType.UnitPrice], context),
            lineTotal = extractNumericField(fields[ColumnType.LineTotal], context),
            confidence = 0.8f,
            evidenceRefs = row.tokens.map { it.evidenceRef }
        )
    }

    private fun extractLineFallback(row: Row, context: NumericContext, index: Int): ParsedInvoiceLineCandidate {
        val numericTokens = row.tokens.filter { isNumeric(it.text) }
        val textTokens = row.tokens.filter { !isNumeric(it.text) }
        
        return ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = ParsedField(null, null, 0f),
            description = extractField(textTokens),
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
        val totalMeaningful = text.replace(Regex("[^0-9,.()\\- ]"), "").length
        return totalMeaningful >= text.length / 2
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

        val rowTokens = firstPage.filter { Math.abs(it.top - best.top) < 0.005f }.sortedBy { it.left }
        val name = rowTokens.joinToString(" ") { it.text }

        return ParsedField(name, name, 0.7f, rowTokens.map { it.evidenceRef })
    }

    private fun detectInvoiceNumber(tokens: List<LayoutToken>): ParsedField<String?> {
        val labels = listOf("invoice", "factura", "number", "número", "num", "no.", "inv#", "remisión")
        for (token in tokens) {
            if (labels.any { token.text.lowercase().contains(it) }) {
                val nearby = tokens.filter {
                    it.pageIndex == token.pageIndex &&
                            Math.abs(it.top - token.top) < 0.015f &&
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
        val totalLabels = listOf("total", "importe total", "grand total")
        val taxLabels = listOf("tax", "iva", "itbis", "impuesto")
        val subtotalLabels = listOf("subtotal", "sub-total", "sub total")
        val discountLabels = listOf("discount", "descuento")
        val feesLabels = listOf("fee", "cargo", "flete", "shipping", "delivery")

        return TotalsResult(
            subtotal = findAmountNearLabel(tokens, subtotalLabels, context),
            discount = findAmountNearLabel(tokens, discountLabels, context),
            fees = findAmountNearLabel(tokens, feesLabels, context),
            tax = findAmountNearLabel(tokens, taxLabels, context),
            total = findAmountNearLabel(tokens, totalLabels, context),
            currency = ParsedField(null, null, 0f)
        )
    }

    private fun findAmountNearLabel(tokens: List<LayoutToken>, labels: List<String>, context: NumericContext): ParsedField<BigDecimal?> {
        for (token in tokens) {
            if (labels.any { token.text.lowercase().contains(it) }) {
                val nearby = tokens.filter {
                    it.pageIndex == token.pageIndex &&
                            Math.abs(it.top - token.top) < 0.015f &&
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
        val sanitized = text.replace(Regex("[^0-9,.-]"), "")
        if (sanitized.isBlank()) return null
        
        return try {
            val normalized = if (context.decimalSeparator == ',') {
                sanitized.replace(".", "").replace(",", ".")
            } else {
                sanitized.replace(",", "")
            }
            // Handle (12.34) as -12.34
            val final = if (text.contains("(") && text.contains(")")) "-$normalized" else normalized
            BigDecimal(final)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateOverallConfidence(lines: List<ParsedInvoiceLineCandidate>, totals: TotalsResult, hasLayout: Boolean): Float {
        var score = 0.0f
        if (hasLayout) score += 0.3f
        if (totals.total.normalizedValue != null) score += 0.2f
        if (lines.isNotEmpty()) {
            val avgLineConf = lines.map { it.confidence ?: 0f }.sum() / lines.size
            score += avgLineConf * 0.5f
        }
        return Math.min(score, 1.0f)
    }

    private data class LayoutToken(
        val text: String,
        val pageIndex: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val evidenceRef: OcrEvidenceRef
    )

    private data class Row(val tokens: List<LayoutToken>) {
        val pageIndex = tokens.first().pageIndex
        val top = tokens.minOf { it.top }
        val bottom = tokens.maxOf { it.bottom }
        val text = tokens.joinToString(" ") { it.text }
    }

    private data class TableLayout(
        val columns: Map<ColumnType, FloatRange>,
        val isInferred: Boolean = false
    ) {
        fun getColumnType(x: Float): ColumnType? {
            return columns.entries.find { it.value.contains(x) }?.key
        }

        companion object {
            fun fromHeaderRow(row: Row): TableLayout {
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
                
                // If we found some columns but not Description, and there's a large gap, infer it
                if (!cols.containsKey(ColumnType.Description)) {
                    // Heuristic: largest gap between columns or leftmost large area
                }
                
                return TableLayout(cols)
            }
        }
    }

    private enum class ColumnType {
        SKU, Description, Quantity, Package, UnitPrice, LineTotal
    }

    private data class FloatRange(val start: Float, val end: Float) {
        fun contains(v: Float) = v in start..end
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
