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
            val result = rows.filter { row -> 
                !isRowConsumed(row) && 
                row.tokens.count { parser.isNumeric(it.text) } >= 2 && 
                row.tokens.any { it.text.length > 2 && !parser.isNumeric(it.text) }
            }.map { LogicalItem(it) }
            // println("DEBUG Assembler: Fallback found ${result.size} items from ${rows.size} rows")
            return result
        }

        val items = mutableListOf<LogicalItem>()
        val consumedIndices = mutableSetOf<Int>()

        for (i in rows.indices) {
            if (i in consumedIndices || isRowConsumed(rows[i])) continue
            
            val row = rows[i]
            if (parser.isHeaderRow(row)) continue

            // A primary row MUST have identity.
            if (!isPotentialIdentityRow(row, layout)) continue
            
            // For a primary row, we also require it to either have money/qty OR be followed by a row that completes it.
            val hasMoneyOrQty = startsNewItem(row, layout)
            val nextRow = rows.getOrNull(i + 1)?.takeIf { !isRowConsumed(it) && it.pageIndex == row.pageIndex }
            val followedByMoney = nextRow != null && isComplementaryMoneyRow(listOf(row), nextRow, layout) && !startsNewItem(nextRow, layout)
            
            if (!hasMoneyOrQty && !followedByMoney) {
                // println("DEBUG Assembler: Skipping row ${row.text} - no money/qty and not followed by money")
                continue
            }

            val currentGroup = mutableListOf(row)
            consumedIndices.add(i)

            var j = i + 1
            while (j < rows.size && j < i + 4) {
                if (j in consumedIndices || isRowConsumed(rows[j])) {
                    j++
                    continue
                }

                val nextCandidate = rows[j]
                if (nextCandidate.pageIndex != row.pageIndex) break
                
                // If the next row starts a new logical item, don't merge.
                if (startsNewItem(nextCandidate, layout)) {
                    // Exception: merge if current lacks money AND next is aligned with description.
                    // This handles items where description spans multiple rows and money is on the last one.
                    if (!hasMoney(currentGroup, layout) && isDescriptionContinuation(currentGroup.last(), nextCandidate, layout, rows.getOrNull(j + 1), ignoreNumeric = true)) {
                        currentGroup.add(nextCandidate)
                        consumedIndices.add(j)
                        // If this row completes the money requirement, stop merging for this item.
                        if (hasMoney(listOf(nextCandidate), layout)) break
                        j++
                        continue
                    } else {
                        break
                    }
                }

                if (isUPCContinuation(nextCandidate)) {
                    currentGroup.add(nextCandidate)
                    consumedIndices.add(j)
                    j++
                    continue
                }

                if (isMoneyContinuation(currentGroup, nextCandidate, layout)) {
                    currentGroup.add(nextCandidate)
                    consumedIndices.add(j)
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

        // println("DEBUG Assembler: Returning ${items.size} logical items")
        return items.sortedBy { it.primaryRow.top }
    }

    private fun startsNewItem(row: Row, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        val hasIdentity = isPotentialIdentityRow(row, layout)
        val hasMoney = hasMoney(listOf(row), layout)
        val hasQty = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Quantity && parser.isNumeric(it.text) }
        
        // A row starts a new item if it has identity AND (money OR quantity)
        return hasIdentity && (hasMoney || hasQty)
    }

    private fun isPotentialIdentityRow(row: Row, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        // A row has identity if it has text in the description column or SKU column.
        val hasSku = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.SKU }
        val hasDescription = row.tokens.any { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Description && it.text.any { c -> c.isLetter() } }
        
        // Identity rows (products/descriptions) are almost always on the left ~70% of the page.
        // This avoids misidentifying right-aligned summary labels as items.
        val onLeft = row.tokens.any { it.left < 0.7f }
        
        return (hasSku || hasDescription) && onLeft
    }

    private fun isUPCContinuation(row: Row): Boolean {
        // Example: "UPC5265800837"
        val text = row.text.trim().uppercase()
        return text.startsWith("UPC") || (text.length >= 8 && text.all { it.isDigit() })
    }

    private fun isMoneyContinuation(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        // A row is a money continuation if the current group lacks money (UnitPrice or LineTotal)
        // and the next row HAS money in those columns, but lacks identity.
        
        if (hasMoney(currentGroup, layout)) return false
        
        val nextHasMoney = nextRow.tokens.any { t ->
            val type = layout.getColumnType(t.centerX)
            (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                DeterministicPurchaseInvoiceParser().isNumeric(t.text)
        }
        
        if (!nextHasMoney) return false
        
        val nextHasIdentity = isPotentialIdentityRow(nextRow, layout)
        // Strong signal: B completes A if B has money but no identity.
        return !nextHasIdentity
    }

    private fun hasMoney(group: List<Row>, layout: DeterministicPurchaseInvoiceParser.PageLayout): Boolean {
        return group.any { row ->
            row.tokens.any { t ->
                val type = layout.getColumnType(t.centerX)
                (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                    DeterministicPurchaseInvoiceParser().isNumeric(t.text)
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
        val nextHasMoney = nextRow.tokens.any { t ->
            val type = layout.getColumnType(t.centerX)
            (type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) &&
                parser.isNumeric(t.text)
        }
        
        return nextHasMoney
    }

    private fun isDescriptionContinuation(
        previous: Row,
        next: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout,
        following: Row?,
        ignoreNumeric: Boolean = false
    ): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        if (!ignoreNumeric && next.tokens.count { parser.isNumeric(it.text) } >= 2) return false
        
        val gap = next.top - previous.bottom
        // Allow slightly larger gaps (up to ~1 line) to support fixtures with empty lines or sparse layouts.
        if (gap < -0.005f || gap > 0.06f) return false 

        val prevDesc = previous.tokens.filter { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Description }
        val prevSku = previous.tokens.filter { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.SKU }
        
        val nextDesc = next.tokens.filter { layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Description }
        
        if (prevDesc.isEmpty() && prevSku.isEmpty()) return false
        
        val prevLeft = prevDesc.minOfOrNull { it.left } ?: prevSku.minOf { it.left }
        val nextLeft = nextDesc.minOfOrNull { it.left } ?: next.tokens.filter { it.text.any(Char::isLetter) }.minOfOrNull { it.left } ?: next.tokens.minOf { it.left }
        
        if (kotlin.math.abs(nextLeft - prevLeft) > 0.08f) return false

        // Ambiguity check: if it's too close to the NEXT potential item, don't merge.
        if (following != null && following.pageIndex == next.pageIndex) {
            val gapToFollowing = following.top - next.bottom
            if (gapToFollowing <= gap * 1.1f && startsNewItem(following, layout)) return false
        }

        return true
    }
}
