package com.miara.cuentame.core.ocr.parser

/**
 * Handles combining multiple visual [Row]s into single logical invoice items.
 */
internal object LogicalInvoiceItemAssembler {

    data class LogicalItem(
        val primaryRow: Row,
        val continuationRows: List<Row> = emptyList()
    ) {
        val allRows: List<Row> = listOf(primaryRow) + continuationRows
        val allTokens: List<LayoutToken> = allRows.flatMap { it.tokens }
    }

    /**
     * Groups physical rows into logical items based on structural complementarity.
     */
    fun assemble(
        rows: List<Row>,
        layout: DeterministicPurchaseInvoiceParser.PageLayout?,
        isRowConsumed: (Row) -> Boolean
    ): List<LogicalItem> {
        val parser = DeterministicPurchaseInvoiceParser()
        if (layout == null || layout.source == DeterministicPurchaseInvoiceParser.LayoutSource.Unknown) {
            return rows.filter { row -> 
                !isRowConsumed(row) && 
                row.tokens.count { parser.isNumeric(it.text) } >= 2 && 
                row.tokens.any { it.text.length > 2 && !parser.isNumeric(it.text) }
            }.map { LogicalItem(it) }
        }

        val items = mutableListOf<LogicalItem>()
        val consumedIndices = mutableSetOf<Int>()

        for (i in rows.indices) {
            if (i in consumedIndices || isRowConsumed(rows[i])) continue
            
            val row = rows[i]
            if (parser.isHeaderRow(row)) {
                consumedIndices.add(i)
                continue
            }

            // A primary row MUST have identity.
            if (!isPotentialIdentityRow(row, layout)) continue
            
            val hasMoneyOrQty = startsNewItem(row, layout)
            
            // Find next non-empty unconsumed row on the SAME page
            var nextRow: Row? = null
            for (k in i + 1 until rows.size) {
                if (k in consumedIndices || isRowConsumed(rows[k])) continue
                if (rows[k].pageIndex != row.pageIndex) break
                if (rows[k].text.isNotBlank()) {
                    nextRow = rows[k]
                    break
                }
            }
            
            val followedByMoney = nextRow != null && 
                isComplementaryMoneyRow(listOf(row), nextRow, layout) && 
                !startsNewItem(nextRow, layout) &&
                !parser.isHeaderRow(row)
            
            if (!hasMoneyOrQty && !followedByMoney) continue

            val currentGroup = mutableListOf(row)
            consumedIndices.add(i)

            var j = i + 1
            while (j < rows.size && j < i + 8) {
                if (j in consumedIndices || isRowConsumed(rows[j])) {
                    j++
                    continue
                }

                val nextCandidate = rows[j]
                if (nextCandidate.text.isBlank()) { j++; continue }
                if (nextCandidate.pageIndex != row.pageIndex) break
                
                // If the next row starts a new logical item, it's a hard boundary. 
                if (startsNewItem(nextCandidate, layout)) break

                if (isUPCContinuation(nextCandidate)) {
                    currentGroup.add(nextCandidate)
                    consumedIndices.add(j)
                    j++
                    continue
                }

                if (isMoneyContinuation(currentGroup, nextCandidate, layout)) {
                    currentGroup.add(nextCandidate)
                    consumedIndices.add(j)
                    // Once money is found for an item, we stop merging to avoid swallowing 
                    // unrelated text or subsequent items.
                    break 
                }
                
                if (isDescriptionContinuation(currentGroup.last(), nextCandidate, layout, rows.getOrNull(j + 1))) {
                     currentGroup.add(nextCandidate)
                     consumedIndices.add(j)
                     j++
                     continue
                }

                break
            }
            items.add(LogicalItem(currentGroup.first(), currentGroup.drop(1)))
        }

        return items.sortedBy { it.primaryRow.top }
    }

    private fun startsNewItem(row: Row, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        val hasIdentity = isPotentialIdentityRow(row, layout)
        val hasMoney = hasMoney(listOf(row), layout)
        val hasQty = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Quantity && parser.isNumeric(it.text) }
        
        return hasIdentity && (hasMoney || hasQty)
    }

    private fun isPotentialIdentityRow(row: Row, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        val hasSku = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.SKU }
        val hasDescription = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Description && it.text.any { c -> c.isLetter() } }
        val onLeft = row.tokens.any { it.left < 0.75f }
        return (hasSku || hasDescription) && onLeft
    }

    private fun isUPCContinuation(row: Row): Boolean {
        val text = row.text.trim().uppercase()
        return text.startsWith("UPC") || (text.length >= 8 && text.filter { it.isDigit() }.length >= 8)
    }

    private fun isMoneyContinuation(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        val nextHasMoney = nextRow.tokens.any { t ->
            val type = layout.getColumnType(t.centerX)
            (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                parser.isNumeric(t.text)
        }
        if (!nextHasMoney) return false
        
        // A money continuation is valid if the current group is missing money
        // OR if the next row doesn't look like a new item.
        return !hasMoney(currentGroup, layout) || !startsNewItem(nextRow, layout)
    }

    private fun hasMoney(group: List<Row>, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        return group.any { row ->
            row.tokens.any { t ->
                val type = layout.getColumnType(t.centerX)
                (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                    parser.isNumeric(t.text)
            }
        }
    }

    private fun isComplementaryMoneyRow(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        if (hasMoney(currentGroup, layout)) return false
        val parser = DeterministicPurchaseInvoiceParser()
        return nextRow.tokens.any { t ->
            val type = layout.getColumnType(t.centerX)
            (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                parser.isNumeric(t.text)
        }
    }

    private fun isDescriptionContinuation(
        previous: Row,
        next: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout,
        following: Row?
    ): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        // A description continuation should not contain multiple numeric tokens (likely a new product)
        if (next.tokens.count { parser.isNumeric(it.text) } >= 2) return false
        
        val gap = next.top - previous.bottom
        // Plausible vertical proximity for a continuation.
        if (gap < -0.005f || gap > 0.10f) return false 

        val nextLeft = next.tokens.minOfOrNull { it.left } ?: 1.0f
        if (nextLeft > 0.75f || !next.text.any { it.isLetter() }) return false

        val prevIdentity = previous.tokens.filter { t -> 
            val type = layout.getColumnType(t.centerX)
            type == DeterministicPurchaseInvoiceParser.ColumnType.Description || type == DeterministicPurchaseInvoiceParser.ColumnType.SKU
        }
        
        val alignmentGap = if (prevIdentity.isNotEmpty()) {
            prevIdentity.minOf { t -> kotlin.math.abs(nextLeft - t.left) }
        } else {
            kotlin.math.abs(nextLeft - previous.tokens.minOf { it.left })
        }

        // Tighten alignment for description continuations.
        if (alignmentGap <= 0.15f) {
            // Ambiguity check: avoid swallowing orphans if perfectly aligned with next item.
            if (alignmentGap < 0.01f && following != null && startsNewItem(following, layout)) {
                val followingLeft = following.tokens.filter { it.text.any(Char::isLetter) }.minOfOrNull { it.left } ?: following.tokens.minOf { it.left }
                if (kotlin.math.abs(nextLeft - followingLeft) < 0.01f) return false
            }
            return true
        }

        return false
    }
}
