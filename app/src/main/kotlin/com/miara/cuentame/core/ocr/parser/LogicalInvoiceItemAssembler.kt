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
                isStrictlyComplementaryMoneyRow(listOf(row), nextRow, layout) && 
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
                if (startsNewItem(nextCandidate, layout)) {
                    break
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
                    // Once money is found for an item, we stop merging to avoid swallowing 
                    // unrelated text or subsequent items.
                    break 
                }

                if (isDescriptionContinuation(currentGroup, nextCandidate, layout, rows.getOrNull(j + 1))) {
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
        // Broaden UPC detection: starts with UPC or is a long numeric-heavy string without being a money row
        return text.startsWith("UPC") || 
            (text.length >= 8 && text.filter { it.isDigit() }.length >= 8 && !text.contains(Regex("[.,][0-9]{2}")))
    }

    private fun isMoneyContinuation(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        return isCredibleMoneyContinuation(currentGroup, nextRow, layout)
    }

    private fun isCredibleMoneyContinuation(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        val parser = DeterministicPurchaseInvoiceParser()
        if (!isStrictlyComplementaryMoneyRow(currentGroup, nextRow, layout)) return false

        val nextHasQty = nextRow.tokens.any { 
            layout.getColumnType(it.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Quantity && 
                parser.isNumeric(it.text) 
        }

        if (nextHasQty) return false
        
        // Ensure no product identity tokens in this row
        if (isPotentialIdentityRow(nextRow, layout)) return false

        if (startsNewItem(nextRow, layout)) return false

        return true
    }

    private fun getMoneyColumnCounts(
        row: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Map<DeterministicPurchaseInvoiceParser.ColumnType, Int> {
        val parser = DeterministicPurchaseInvoiceParser()
        val counts = mutableMapOf<DeterministicPurchaseInvoiceParser.ColumnType, Int>()
        for (t in row.tokens) {
            val type = layout.getColumnType(t.centerX)
            if ((type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || 
                    type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) && 
                parser.isNumeric(t.text)) {
                counts[type] = (counts[type] ?: 0) + 1
            }
        }
        return counts
    }

    private fun isStrictlyComplementaryMoneyRow(
        currentGroup: List<Row>,
        nextRow: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        val missing = missingMoneyColumns(currentGroup, layout)
        if (missing.isEmpty()) return false

        val nextMoneyCounts = getMoneyColumnCounts(nextRow, layout)
        if (nextMoneyCounts.isEmpty()) return false

        // Every money field contributed by a continuation must correspond to a field 
        // currently missing from the logical item. (Non-empty subset rule)
        if (!nextMoneyCounts.keys.all { it in missing }) return false

        // Protect against duplicate tokens inside one money column in the continuation row.
        if (nextMoneyCounts.values.any { it > 1 }) return false

        return true
    }

    private fun missingMoneyColumns(
        group: List<Row>,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Set<DeterministicPurchaseInvoiceParser.ColumnType> {
        val present = mutableSetOf<DeterministicPurchaseInvoiceParser.ColumnType>()
        val parser = DeterministicPurchaseInvoiceParser()
        for (row in group) {
            for (t in row.tokens) {
                val type = layout.getColumnType(t.centerX)
                if ((type == DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice || 
                        type == DeterministicPurchaseInvoiceParser.ColumnType.LineTotal) && 
                    parser.isNumeric(t.text)) {
                    present.add(type)
                }
            }
        }
        val missing = mutableSetOf<DeterministicPurchaseInvoiceParser.ColumnType>()
        if (DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice !in present) missing.add(DeterministicPurchaseInvoiceParser.ColumnType.UnitPrice)
        if (DeterministicPurchaseInvoiceParser.ColumnType.LineTotal !in present) missing.add(DeterministicPurchaseInvoiceParser.ColumnType.LineTotal)
        return missing
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

    private fun hasCredibleDescription(
        group: List<Row>,
        layout: DeterministicPurchaseInvoiceParser.PageLayout
    ): Boolean {
        return group.any { row ->
            row.tokens.any { t ->
                layout.getColumnType(t.centerX) == DeterministicPurchaseInvoiceParser.ColumnType.Description && 
                t.text.any { it.isLetter() }
            }
        }
    }

    private fun isDescriptionContinuation(
        currentGroup: List<Row>,
        next: Row,
        layout: DeterministicPurchaseInvoiceParser.PageLayout,
        following: Row?
    ): Boolean {
        val previous = currentGroup.last()
        val parser = DeterministicPurchaseInvoiceParser()

        // A product is "complete" for description purposes only if it has a credible 
        // description AND its required money structure is sufficiently populated.
        if (hasCredibleDescription(currentGroup, layout) && hasMoney(currentGroup, layout)) {
            // If the item is already money-complete, it rejects further description.
            if (missingMoneyColumns(currentGroup, layout).isEmpty()) return false
            
            // If money is incomplete, we only accept a description continuation if it is 
            // sandwiched by a subsequent row that strictly completes the money structure.
            // This prevents an incomplete product A from swallowing text that might belong 
            // to a following product B or be an orphan.
            if (following == null || !isCredibleMoneyContinuation(currentGroup, following, layout)) {
                return false
            }
        }

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

        // Loosen alignment threshold slightly for multi-row descriptions that might be shifted.
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
