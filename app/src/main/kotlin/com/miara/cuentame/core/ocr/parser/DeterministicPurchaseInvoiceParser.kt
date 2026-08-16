package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

class DeterministicPurchaseInvoiceParser @Inject constructor() : PurchaseInvoiceParser {

    override fun parse(pages: List<OcrPageEvidence>): PurchaseInvoiceParseResult {
        val tokens = normalizeLayoutTokens(pages)

        if (tokens.isEmpty()) {
            return PurchaseInvoiceParseResult.empty()
        }

        val rows = RowClusterer.clusterIntoRows(tokens)
        val orderedTokens = rows.flatMap { it.tokens }
        val numericContext = inferNumericContext(orderedTokens)
        
        val supplier = detectSupplier(orderedTokens)
        val invoiceNumber = detectInvoiceNumber(orderedTokens)
        val date = detectDate(orderedTokens, numericContext)

        val pageLayouts = detectTableLayouts(rows)
        val terminalPosition = findTerminalTotal(rows, pageLayouts)
        
        val summaryIsolation = performSummaryIsolation(rows, numericContext)
        val lineCandidates = extractLines(rows, pageLayouts, numericContext, terminalPosition, summaryIsolation.consumedRowIds)
        // println("DEBUG: Found ${lineCandidates.size} candidates")
        val totals = detectTotals(rows, numericContext, terminalPosition, summaryIsolation)

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
            supplierNameCandidate = supplier, invoiceNumber = invoiceNumber, invoiceDate = date,
            subtotal = totals.subtotal, discount = totals.discount, fees = totals.fees, tax = totals.tax,
            total = totals.total, currency = totals.currency, lines = validatedLines,
            confidence = calculateOverallConfidence(validatedLines, totals, pageLayouts), warnings = warnings
        )
    }

    internal fun normalizeLayoutTokens(pages: List<OcrPageEvidence>): List<LayoutToken> =
        pages.flatMapIndexed { pageIdx, page ->
            if (page.widthPx <= 0 || page.heightPx <= 0) return@flatMapIndexed emptyList<LayoutToken>()
            page.blocks.flatMapIndexed { blockIdx, block ->
                block.lines.flatMapIndexed { lineIdx, line ->
                    line.elements.mapIndexedNotNull { elementIdx, element ->
                        val box = element.boundingBox ?: return@mapIndexedNotNull null
                        LayoutToken(
                            text = element.text,
                            pageIndex = pageIdx,
                            left = box.left.toFloat() / page.widthPx,
                            top = box.top.toFloat() / page.heightPx,
                            right = box.right.toFloat() / page.widthPx,
                            bottom = box.bottom.toFloat() / page.heightPx,
                            ocrConfidence = element.confidence,
                            evidenceRef = OcrEvidenceRef(pageIdx, blockIdx, lineIdx, elementIdx)
                        )
                    }
                }
            }
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
            numericCount >= 1 && row.text.length > 5 && !isHeaderRow(row) &&
                !isSummaryLabelRow(row) && !isNonItemContext(row)
        }
        
        // A single amount column is a valid receipt layout, but it needs repetition.
        if (candidateRows.size < 2) return null

        val supportThreshold = if (candidateRows.size >= 3) 3 else 2

        val packageSpanTokens = candidateRows.flatMap { row ->
            PackageTextDetector.findPackageSpan(row.tokens.map { it.text })?.let { span ->
                row.tokens.subList(span.startIndex, span.endIndexExclusive)
            }.orEmpty()
        }.toSet()
        val numericTokens = candidateRows.flatMap { it.tokens }.filter {
            it !in packageSpanTokens && isNumeric(it.text) && PackageTextDetector.detectPackageText(it.text) == null
        }
        val clusters = RowClusterer.clusterTokensByX(numericTokens, allRows)
            .filter { it.support >= supportThreshold || (candidateRows.size < 3 && it.tokens.any { isMoneyLike(it.text) }) }
            .sortedBy { it.avgLeft }

        if (clusters.isEmpty()) return null
        
        val cols = mutableMapOf<ColumnType, FloatRange>()
        
        val moneyClusters = clusters.filter { c -> 
            val moneyRows = c.tokens.filter { isMoneyLike(it.text) }
                .map { t -> allRows.find { it.tokens.contains(t) }?.rowId }
                .filterNotNull()
                .distinct()
            moneyRows.size >= 2 || (candidateRows.size < 3 && moneyRows.size >= 1)
        }
        val rightmost = if (moneyClusters.isNotEmpty()) moneyClusters.last() else clusters.last()
        
        cols[ColumnType.LineTotal] = rightmost.toRange()

        val earlierMoney = moneyClusters.filter { it != rightmost }
        val unitPriceCluster = earlierMoney.lastOrNull()
        if (unitPriceCluster != null) {
            cols[ColumnType.UnitPrice] = unitPriceCluster.toRange()
        }

        val nonMoney = clusters.filter { it != rightmost && it != unitPriceCluster }
        val quantityCluster = nonMoney
            .filter(::isQuantityLikeCluster)
            .maxByOrNull { quantityEvidence(it, unitPriceCluster, rightmost, allRows) }
        if (quantityCluster != null) cols[ColumnType.Quantity] = quantityCluster.toRange()

        val numericSkuClusters = nonMoney.filter { it != quantityCluster && isCredibleNumericSkuCluster(it) }
        val alphaSkuTokens = candidateRows.flatMap { it.tokens }.filter(::isStrongAlphaNumericCode)
        val alphaSkuClusters = RowClusterer.clusterTokensByX(alphaSkuTokens, allRows)
            .filter { it.support >= supportThreshold }
        val skuCluster = (numericSkuClusters + alphaSkuClusters)
            .maxWithOrNull(compareBy<TokenCluster>({ skuEvidence(it) }, { it.support }))
        if (skuCluster != null) cols[ColumnType.SKU] = skuCluster.toRange(0.03f)

        val packageTokens = candidateRows.flatMap { it.tokens }
            .filter { PackageTextDetector.detectPackageText(it.text) != null }
        RowClusterer.clusterTokensByX(packageTokens, allRows)
            .filter { it.support >= supportThreshold }
            .maxByOrNull { it.support }
            ?.let { cols[ColumnType.Package] = it.toRange(0.03f) }

        // Description is the residual textual content. Specific inferred columns are
        // checked first, so this deliberately broad range supports description-first,
        // quantity-first, and description-then-SKU layouts without positional permutations.
        cols[ColumnType.Description] = FloatRange(0f, 1f)

        // A price without quantity is still useful. A lone amount must not be promoted
        // to both price and total, and an unsupported quantity remains absent.
        if (quantityCluster == null && earlierMoney.isEmpty()) {
            cols.remove(ColumnType.Quantity)
            cols.remove(ColumnType.UnitPrice)
        }
        
        return PageLayout(pageIndex, cols, LayoutSource.GeometricInference)
    }

    private fun extractLines(
        rows: List<Row>,
        pageLayouts: Map<Int, PageLayout>,
        context: NumericContext,
        terminalPosition: DocumentPosition?,
        consumedBySummary: Set<Int>
    ): List<ParsedInvoiceLineCandidate> {
        val candidates = mutableListOf<ParsedInvoiceLineCandidate>()
        var lineIndex = 0
        
        val tableHeaderTopByPage = rows.filter(::isHeaderRow)
            .groupBy { it.pageIndex }
            .mapValues { (_, pageRows) -> pageRows.minOf { it.top } }
        val tableStartByPage = rows.groupBy { it.pageIndex }.mapValues { (page, pageRows) ->
            tableHeaderTopByPage[page] ?: inferTableStart(pageRows, pageLayouts[page])
        }

        val isRowConsumedExternally: (Row) -> Boolean = { row ->
            row.rowId in consumedBySummary ||
            isHeaderRow(row) ||
            tableHeaderTopByPage[row.pageIndex]?.let { row.top <= it } == true ||
            tableStartByPage[row.pageIndex]?.let { row.top < it } == true ||
            terminalPosition?.let { DocumentPosition(row.pageIndex, row.top) >= it } == true ||
            isSummaryLabelRow(row) ||
            (isNonItemContext(row) && row.tokens.count { isNumeric(it.text) } < 2)
        }

        val logicalItems = rows.groupBy { it.pageIndex }.toSortedMap().flatMap { (pageIdx, pageRows) ->
            LogicalInvoiceItemAssembler.assemble(
                pageRows,
                pageLayouts[pageIdx],
                isRowConsumedExternally
            )
        }

        for (item in logicalItems) {
            val primaryRow = item.primaryRow
            val layout = pageLayouts[primaryRow.pageIndex] ?: continue
            
            val candidate = if (layout.source != LayoutSource.Unknown) {
                extractLogicalLineWithLayout(item, layout, context, lineIndex++)
            } else {
                extractLineFallback(primaryRow, context, lineIndex++)
            }

            if (hasItemIdentity(candidate)) {
                candidates.add(candidate)
            }
        }
        return candidates
    }

    private fun extractLogicalLineWithLayout(
        item: LogicalInvoiceItemAssembler.LogicalItem,
        layout: PageLayout,
        context: NumericContext,
        index: Int
    ): ParsedInvoiceLineCandidate {
        val fields = mutableMapOf<ColumnType, MutableList<LayoutToken>>()
        item.allTokens.forEach { t ->
            val type = layout.getColumnType(t.centerX)
            if (type != null) {
                fields.getOrPut(type) { mutableListOf() }.add(t)
            }
        }

        // Heuristic: If description is missing but SKU column has multiple tokens from different 
        // rows, those secondary tokens are almost certainly misaligned description content.
        val skuTokens = fields[ColumnType.SKU]
        if ((fields[ColumnType.Description] == null || fields[ColumnType.Description]!!.isEmpty()) &&
            skuTokens != null && skuTokens.size > 1) {
            val firstLineIdx = skuTokens.map { it.evidenceRef.lineIndex }.filterNotNull().minOrNull() ?: 0
            val potentialDesc = skuTokens.filter { (it.evidenceRef.lineIndex ?: 0) > firstLineIdx }
            if (potentialDesc.isNotEmpty()) {
                fields.getOrPut(ColumnType.Description) { mutableListOf() }.addAll(potentialDesc)
                skuTokens.removeAll(potentialDesc)
            }
        }

        val descTokens = fields[ColumnType.Description] ?: emptyList()
        val rawDescription = extractField(descTokens)
        
        val packageSpan = PackageTextDetector.findPackageSpan(item.allTokens.map { it.text })
        val packageFromDesc = packageSpan?.text ?: PackageTextDetector.findPackageToken(item.allTokens.map { it.text })
            ?: PackageTextDetector.detectPackageText(rawDescription.detectedText ?: "")
        
        val packageField: ParsedField<String?> = if (packageFromDesc != null) {
            val packageTokens = packageSpan?.let { item.allTokens.subList(it.startIndex, it.endIndexExclusive) }
                ?: item.allTokens.filter { packageFromDesc.contains(it.text, ignoreCase = true) }
            ParsedField(packageFromDesc, packageFromDesc, 0.8f, packageTokens.map { it.evidenceRef }.ifEmpty { rawDescription.evidenceRefs })
        } else {
            extractField(fields[ColumnType.Package])
        }
        
        val finalDescription = if (packageFromDesc != null) {
            val packageParts = packageSpan?.let { item.allTokens.subList(it.startIndex, it.endIndexExclusive).map(LayoutToken::text).toSet() }.orEmpty()
            val cleaned = rawDescription.detectedText?.split(" ")?.filterNot { part -> part in packageParts }?.joinToString(" ")
                ?.replace(packageFromDesc, "", ignoreCase = true)?.trim()
            rawDescription.copy(detectedText = cleaned, normalizedValue = cleaned)
        } else {
            rawDescription
        }

        return ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = extractField(fields[ColumnType.SKU]),
            description = finalDescription,
            quantity = extractNumericField(fields[ColumnType.Quantity]?.filterNot { token ->
                packageSpan?.let { span -> item.allTokens.indexOf(token) in span.startIndex until span.endIndexExclusive } == true
            }, context),
            packageText = packageField,
            unitPrice = extractNumericField(fields[ColumnType.UnitPrice], context),
            lineTotal = extractNumericField(fields[ColumnType.LineTotal], context),
            confidence = when(layout.source) {
                LayoutSource.SemanticHeader -> 0.9f
                LayoutSource.CompatibleContinuation -> 0.85f
                LayoutSource.GeometricInference -> 0.7f
                else -> 0.5f
            },
            evidenceRefs = item.allTokens.map { it.evidenceRef }
        )
    }

    private fun inferTableStart(pageRows: List<Row>, layout: PageLayout?): Float {
        if (layout == null || layout.source == LayoutSource.Unknown) return pageRows.firstOrNull()?.top ?: 0f
        val compatible = pageRows.filter { row ->
            !isNonItemContext(row) && row.tokens.any { token ->
                layout.getColumnType(token.centerX) in setOf(ColumnType.Quantity, ColumnType.UnitPrice, ColumnType.LineTotal) &&
                    isNumeric(token.text)
            }
        }
        return compatible.firstOrNull()?.top ?: pageRows.firstOrNull()?.top ?: 0f
    }

    private fun findTerminalTotal(rows: List<Row>, layouts: Map<Int, PageLayout>): DocumentPosition? {
        val candidates = rows.filter(::isTerminalTotalRow)
        return candidates.firstOrNull { row ->
            val position = DocumentPosition(row.pageIndex, row.top)
            rows.any { prior -> DocumentPosition(prior.pageIndex, prior.top) < position && looksLikeItemRow(prior) } &&
                !hasCredibleItemContinuation(rows, position, layouts)
        }?.let { DocumentPosition(it.pageIndex, it.top) }
    }

    private fun hasCredibleItemContinuation(rows: List<Row>, position: DocumentPosition, layouts: Map<Int, PageLayout>): Boolean =
        rows.filter { it.pageIndex > position.pageIndex }.any { row ->
            val layout = layouts[row.pageIndex]
            !isNonItemContext(row) && !isSummaryLabelRow(row) && row.tokens.any { token ->
                isNumeric(token.text) && (layout?.getColumnType(token.centerX) in setOf(ColumnType.Quantity, ColumnType.UnitPrice, ColumnType.LineTotal))
            } && row.tokens.any { it.text.any(Char::isLetter) }
        }

    private fun isTerminalTotalRow(row: Row): Boolean {
        val text = row.text.trim().lowercase()
        if (Regex("\\b(page total|page subtotal|carried forward|continued)\\b").containsMatchIn(text)) return false
        if (isHeaderRow(row)) return false
        val amountTokens = row.tokens.filter { TERMINAL_MONEY.matches(it.text.trim()) }
        if (amountTokens.size != 1) return false
        val label = row.tokens.filterNot { it == amountTokens.single() }
            .joinToString(" ") { it.text.lowercase().trimEnd(':', '.', ',') }.trim().replace(Regex("\\s+"), " ")
        if (label !in TERMINAL_LABELS) return false

        // Exact summary label + one strict amount is summary structure. Product rows
        // with quantity/price/pack fields necessarily carry additional tokens.
        return row.tokens.none { token ->
            token != amountTokens.single() && (isNumeric(token.text) || PackageTextDetector.detectPackageText(token.text) != null)
        }
    }

    private fun looksLikeItemRow(row: Row) =
        row.tokens.any { isNumeric(it.text) } && row.tokens.any { token ->
            token.text.any(Char::isLetter) && !isSummaryLabelRow(row)
        }

    internal fun isSummaryLabelRow(row: Row): Boolean {
        // Items typically have at least two numeric components (e.g., SKU/Qty and Amount).
        // Summary rows should be minimalist to avoid swallowing valid product rows.
        if (row.tokens.count { isNumeric(it.text) } >= 2) return false
        
        val words = row.tokens.filterNot { isNumeric(it.text) }.joinToString(" ") { it.text.lowercase().trimEnd(':', '.', ',') }
            .trim().replace(Regex("\\s+"), " ")
        return words in TERMINAL_LABELS || words in INTERMEDIATE_SUMMARY_LABELS ||
            words in setOf("subtotal", "sub total", "sub-total", "tax", "iva", "itbis", "discount", "descuento", "fees", "fee")
    }

    private fun hasItemIdentity(candidate: ParsedInvoiceLineCandidate): Boolean {
        val description = candidate.description.normalizedValue.orEmpty().trim()
        if (description.length >= 2 && description.any { it.isLetter() }) return true
        
        val sku = candidate.vendorCode.normalizedValue.orEmpty().trim()
        return sku.length >= 2 && sku.any { it.isLetter() }
    }

    internal fun isNonItemContext(row: Row): Boolean {
        val text = row.text.lowercase()
        return NON_ITEM_CONTEXT.containsMatchIn(text)
    }

    internal fun isHeaderRow(row: Row): Boolean {
        if (row.tokens.count { isNumeric(it.text) } >= 3) return false
        val text = row.text.lowercase()
        
        // Exact token match is more robust than full-row regex for some OCR layouts.
        val tokenTexts = row.tokens.map { it.text.lowercase().trimEnd(':', '.') }
        
        val headerWords = setOf("qty", "item", "description", "rate", "amount", "price", "total", "sku", "code", "cant", "precio", "importe")
        if (tokenTexts.count { it in headerWords } >= 3) return true

        val semanticColumns = listOf(
            HeaderAlias.SKU,
            HeaderAlias.DESCRIPTION,
            HeaderAlias.QTY,
            HeaderAlias.PACK,
            HeaderAlias.PRICE,
            HeaderAlias.AMOUNT
        ).count { aliases -> 
            aliases.any { alias -> 
                tokenTexts.any { it == alias } || 
                text.contains(Regex("\\b${Regex.escape(alias)}\\b"))
            }
        }
        
        return semanticColumns >= 2
    }

    private companion object {
        val TERMINAL_LABELS = setOf("total", "grand total", "amount due", "total due", "total a pagar", "importe total")
        val INTERMEDIATE_SUMMARY_LABELS = setOf("page total", "page subtotal", "carried forward", "brought forward", "continued")
        val TERMINAL_MONEY = Regex("^\\$?(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}$")
        val SUPPLIER_METADATA = Regex(
            "\\b(invoice|factura|date|fecha|customer|client|ship to|bill to|phone|email|web|account|order|purchase order|po|page)\\b",
            RegexOption.IGNORE_CASE
        )
        val EMAIL_PHONE_ADDRESS = Regex(
            "(?:@|https?://|www\\.|\\b(?:tel|phone|fax)\\b|\\b\\d{3}[-.) ]+\\d{3}[- ]+\\d{4}\\b|\\b\\d{3,6}\\s+[a-z]+\\s+(?:st|street|ave|avenue|rd|road|blvd|drive|dr)\\b)",
            RegexOption.IGNORE_CASE
        )
        val PAYMENT_FOOTER = Regex("\\b(cash|card|credit|debit|visa|mastercard|amex|paid|payment|tender|change|auth(?:orization)?|balance)\\b|\\*{4}\\d{2,}", RegexOption.IGNORE_CASE)
        val NON_ITEM_CONTEXT = Regex(
            "\\b(sub[ -]?total|grand total|amount due|balance due|freight|shipping|" +
                "invoice|factura|account|customer|purchase order|po[ #:.-]|phone|telephone|tel[.: ]|fax|" +
                "page[ #:.-]|p[aá]gina|route|delivery date|due date)\\b",
            RegexOption.IGNORE_CASE
        )
    }

    private fun isQuantityLikeCluster(cluster: TokenCluster): Boolean = cluster.tokens.all { token ->
        val value = token.text.trim()
        val number = value.toBigDecimalOrNull()
        number != null && number > BigDecimal.ZERO && number < BigDecimal("1000") && 
            (value.contains('.') || value == "0" || !value.startsWith('0'))
    }

    private fun quantityEvidence(
        quantity: TokenCluster,
        price: TokenCluster?,
        total: TokenCluster,
        rows: List<Row>
    ): Int {
        var score = quantity.support * 2
        if (price == null) return score
        val byRow = rows.associateBy { it.rowId }
        val sharedRows = quantity.rowIds intersect price.rowIds intersect total.rowIds
        sharedRows.forEach { rowId ->
            val row = byRow[rowId] ?: return@forEach
            val qty = row.tokens.firstOrNull { quantity.toRange().contains(it.centerX) }
                ?.text?.trim()?.toBigDecimalOrNull()
            val unitPrice = row.tokens.firstOrNull { price.toRange().contains(it.centerX) }
                ?.text?.replace(",", "")?.replace("$", "")?.toBigDecimalOrNull()
            val lineTotal = row.tokens.firstOrNull { total.toRange().contains(it.centerX) }
                ?.text?.replace(",", "")?.replace("$", "")?.toBigDecimalOrNull()
            if (qty != null && unitPrice != null && lineTotal != null &&
                qty.multiply(unitPrice).subtract(lineTotal).abs() <= BigDecimal("0.02")) {
                score += 10
            }
        }
        return score
    }

    private fun isCredibleNumericSkuCluster(cluster: TokenCluster): Boolean = cluster.tokens.all { token ->
        val value = token.text.trim()
        value.all(Char::isDigit) && value.length >= 4 &&
            (value.startsWith('0') || value.toLongOrNull()?.let { it > 999L } == true)
    }

    private fun isStrongAlphaNumericCode(token: LayoutToken): Boolean {
        val value = token.text.trim().trimEnd('.', ':')
        return value.length >= 3 && value.any(Char::isLetter) && value.any(Char::isDigit) &&
            value.all { it.isLetterOrDigit() || it == '-' }
    }

    private fun skuEvidence(cluster: TokenCluster): Int = cluster.tokens.sumOf { token ->
        val value = token.text.trim()
        when {
            value.any(Char::isLetter) && value.any(Char::isDigit) -> 5
            value.startsWith('0') && value.length >= 4 -> 4
            value.length >= 6 -> 3
            else -> 0
        }
    }

    private fun extractLineFallback(row: Row, context: NumericContext, index: Int): ParsedInvoiceLineCandidate {
        val numericTokens = row.tokens.filter { isNumeric(it.text) }
        val textTokens = row.tokens.filter { !isNumeric(it.text) }
        
        return ParsedInvoiceLineCandidate(
            index = index,
            // Unknown layout has no column evidence. Do not manufacture a SKU or
            // remove a tentative token from the description.
            vendorCode = ParsedField(null, null, 0f),
            description = extractField(textTokens),
            quantity = if (numericTokens.size >= 3) extractNumericField(listOf(numericTokens[0]), context) else ParsedField(null, null, 0f),
            packageText = ParsedField(null, null, 0f),
            unitPrice = if (numericTokens.size >= 3) extractNumericField(listOf(numericTokens[1]), context) else ParsedField(null, null, 0f),
            lineTotal = if (numericTokens.isNotEmpty()) extractNumericField(listOf(numericTokens.last()), context) else ParsedField(null, null, 0f),
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
        // if (value == null) println("DEBUG Parser: Failed to parse numeric field from '$text'")
        return ParsedField(text, value, if (value != null) 0.9f else 0.1f, tokens.map { it.evidenceRef })
    }

    internal fun isNumeric(text: String): Boolean {
        val sanitized = text.replace(Regex("[^0-9,.-]"), "")
        if (sanitized.isEmpty() || !sanitized.any { it.isDigit() }) return false
        
        // Alphanumeric SKUs like "DSALAM1" or "FYUC000" should not be considered strictly numeric.
        val digits = text.count { it.isDigit() }
        val letters = text.count { it.isLetter() }
        
        return digits > letters
    }
    
    private fun isMoneyLike(text: String): Boolean {
        return text.contains(Regex("[.,][0-9]{2}")) || text.contains("$")
    }

    private fun detectSupplier(tokens: List<LayoutToken>): ParsedField<String?> {
        val firstPage = tokens.filter { it.pageIndex == 0 }
        val candidates = RowClusterer.clusterIntoRows(firstPage).filter { row ->
            row.top < 0.3f && row.text.length > 2
        }.map { row ->
            val text = row.text.trim()
            val lower = text.lowercase()
            var score = 0
            if (text.any(Char::isLetter)) score += 3
            if (row.tokens.size >= 2) score += 2
            if (Regex("\\b(inc|llc|ltd|corp|company|co\\.?|foods|market|distribuidora|distributor)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) score += 3
            if (SUPPLIER_METADATA.containsMatchIn(lower)) score -= 8
            if (EMAIL_PHONE_ADDRESS.containsMatchIn(lower)) score -= 6
            row to score
        }.sortedWith(compareByDescending<Pair<Row, Int>> { it.second }.thenBy { it.first.top })

        if (candidates.isEmpty()) return ParsedField(null, null, 0f)
        val best = candidates.first().first
        val rowTokens = best.tokens.sortedBy { it.left }
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

    private fun detectTotals(
        rows: List<Row>,
        context: NumericContext,
        terminalPosition: DocumentPosition?,
        summaryIsolation: SummaryIsolationResult
    ): TotalsResult {
        val totalLabels = listOf("total", "importe total", "grand total")
        val taxLabels = listOf("tax", "iva", "itbis", "impuesto")
        val subtotalLabels = listOf("subtotal", "sub-total", "sub total")
        val discountLabels = listOf("discount", "descuento")
        val feesLabels = listOf("fee", "cargo", "flete", "shipping", "delivery")

        return TotalsResult(
            subtotal = summaryIsolation.totals["subtotal"] ?: findAmountNearLabel(rows, subtotalLabels, context),
            discount = summaryIsolation.totals["discount"] ?: findAmountNearLabel(rows, discountLabels, context),
            fees = summaryIsolation.totals["fees"] ?: findAmountNearLabel(rows, feesLabels, context),
            tax = summaryIsolation.totals["tax"] ?: findAmountNearLabel(rows, taxLabels, context),
            total = summaryIsolation.totals["total"] ?: (terminalPosition?.let { position ->
                rows.firstOrNull { it.pageIndex == position.pageIndex && it.top == position.top }
                    ?.let { amountFromRow(it, context) }
            } ?: ParsedField(null, null, 0f)),
            currency = ParsedField(null, null, 0f)
        )
    }

    private fun performSummaryIsolation(
        rows: List<Row>,
        context: NumericContext
    ): SummaryIsolationResult {
        val consumedRows = mutableSetOf<Int>()
        val totals = mutableMapOf<String, ParsedField<BigDecimal?>>()
        
        val finalLabelGroups = mapOf(
            "subtotal" to listOf("subtotal", "sub-total", "sub total"),
            "tax" to listOf("tax", "iva", "itbis", "impuesto"),
            "discount" to listOf("discount", "descuento"),
            "fees" to listOf("fee", "fees", "cargo", "flete", "shipping", "delivery", "delivery fee", "shipping fee"),
            "total" to TERMINAL_LABELS.toList()
        )

        for (i in rows.indices) {
            val row = rows[i]
            if (row.rowId in consumedRows) continue
            
            // 1. Check for intermediate summaries first (isolated but not final)
            if (isCredibleSummaryRow(row, INTERMEDIATE_SUMMARY_LABELS)) {
                 // Intermediate total found. Isolate it so it doesn't become a product.
                 consumeSummaryRow(row, rows, i, context, consumedRows)
                 continue
            }

            // 2. Check for final summaries
            for ((key, labels) in finalLabelGroups) {
                if (totals.containsKey(key)) continue
                
                if (isCredibleSummaryRow(row, labels.toSet())) {
                    val amount = consumeSummaryRow(row, rows, i, context, consumedRows)
                    if (amount != null) {
                        totals[key] = amount
                        break
                    }
                }
            }
        }
        
        return SummaryIsolationResult(totals, consumedRows)
    }

    private fun isCredibleSummaryRow(row: Row, labels: Set<String>): Boolean {
        // Filter out purely numeric tokens to get the label text
        val labelTokens = row.tokens.filter { token ->
            val text = token.text
            !isNumeric(text) && text.any { it.isLetter() }
        }
        val labelText = labelTokens.joinToString(" ") { it.text.lowercase().trimEnd(':', '.', ',') }.trim()
        
        if (labelText.isEmpty()) return false

        // Match label strictly as the whole phrase. 
        val matchesLabel = labels.any { label -> labelText == label }
        
        if (!matchesLabel) return false
        
        // Ignore percentages (rates) when counting amounts.
        val numericCount = row.tokens.count { isNumeric(it.text) && !it.text.contains('%') }
        
        // A summary row should have exactly one amount (on the same row) or zero (if split).
        if (numericCount >= 2) return false
        
        return true
    }

    private fun consumeSummaryRow(
        row: Row,
        allRows: List<Row>,
        index: Int,
        context: NumericContext,
        consumedRows: MutableSet<Int>
    ): ParsedField<BigDecimal?>? {
        var amount = amountFromRow(row, context)
        if (amount.normalizedValue != null) {
            consumedRows.add(row.rowId)
            return amount
        }
        
        // Lookahead for split summary
        if (row.tokens.none { isNumeric(it.text) }) {
            var j = index + 1
            while (j < allRows.size && j < index + 3) {
                val nextRow = allRows[j]
                if (nextRow.pageIndex != row.pageIndex) break
                if (nextRow.rowId in consumedRows) { j++; continue }
                if (nextRow.text.isBlank()) { j++; continue }

                amount = amountFromRow(nextRow, context)
                // Next row must be strictly an amount (no other text) for safe split isolation
                if (amount.normalizedValue != null && nextRow.tokens.size == 1) {
                    consumedRows.add(row.rowId)
                    consumedRows.add(nextRow.rowId)
                    return amount
                }
                break // Found non-empty row that isn't a valid split amount
            }
        }
        return null
    }

    internal data class SummaryIsolationResult(
        val totals: Map<String, ParsedField<BigDecimal?>>,
        val consumedRowIds: Set<Int>
    )

    private fun findAmountNearLabel(
        rows: List<Row>,
        labels: List<String>, 
        context: NumericContext
    ): ParsedField<BigDecimal?> {
        for (row in rows) {
            if (isCredibleSummaryRow(row, labels.toSet())) {
                val amount = amountFromRow(row, context)
                if (amount.normalizedValue != null) return amount
            }
        }
        return ParsedField(null, null, 0f)
    }

    private fun amountFromRow(row: Row, context: NumericContext): ParsedField<BigDecimal?> {
        val valueToken = row.tokens.asReversed().firstOrNull {
            isNumeric(it.text) && !it.text.contains('%')
        }
            ?: return ParsedField(null, null, 0f)
        val value = parseBigDecimal(valueToken.text, context)
        // println("DEBUG amountFromRow: row=${row.text} valueToken=${valueToken.text} value=$value context=${context.decimalSeparator}")
        return ParsedField(valueToken.text, value, if (value != null) 0.9f else 0.1f, row.tokens.map { it.evidenceRef })
    }

    private fun inferNumericContext(tokens: List<LayoutToken>): NumericContext {
        var commaAsDecimal = 0
        var dotAsDecimal = 0
        
        // Use the same regex as parseBigDecimal to find candidates for inference
        val moneyRegex = Regex("[0-9]{1,3}(?:[,.][0-9]{3})*[,.][0-9]{1,3}")
        
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
        if (sanitized.isBlank() || sanitized == "." || sanitized == "-" || sanitized == "," || sanitized == "..") return null
        
        return try {
            val normalized = if (context.decimalSeparator == ',') {
                // Comma-based: 1.234,56 -> 1234.56
                sanitized.replace(".", "").replace(",", ".")
            } else {
                // Dot-based: 1,234.56 -> 1234.56
                sanitized.replace(",", "")
            }
            if (normalized.any { it.isDigit() }) {
                val value = BigDecimal(normalized)
                if (text.contains("(") && text.contains(")")) value.negate() else value
            } else null
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

    internal data class PageLayout(
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

    internal enum class LayoutSource {
        SemanticHeader, CompatibleContinuation, GeometricInference, Unknown
    }

    internal enum class ColumnType {
        SKU, Description, Quantity, Package, UnitPrice, LineTotal
    }

    internal data class NumericContext(
        val decimalSeparator: Char,
        val isAmbiguous: Boolean
    )

    private object HeaderAlias {
        val QTY = listOf("qty", "quantity", "cant", "cantidad", "unidades", "qnt", "qnty")
        val PRICE = listOf("price", "unit price", "precio", "cost", "unit cost", "rate", "p. unit")
        val AMOUNT = listOf("amount", "total", "importe", "ext", "extension", "monto", "net")
        val SKU = listOf("item", "code", "sku", "código", "articulo", "artículo", "no.", "number")
        val DESCRIPTION = listOf("description", "descripción", "desc", "producto", "nombre")
        val PACK = listOf("pack", "size", "empaque", "paquete", "medida", "u/m")
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
