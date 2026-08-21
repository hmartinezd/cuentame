package com.venkoi.restaurantops.core.ocr.parser

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles clustering tokens into visual rows and X-position clusters.
 */
object RowClusterer {

    private const val LEGACY_CENTER_TOLERANCE = 0.005f
    private const val STRONG_CENTER_FACTOR = 0.5f
    private const val MAX_CENTER_FACTOR = 0.8f
    private const val X_BAND_TOLERANCE = 0.03f
    private const val AMBIGUITY_EPSILON = 0.08f

    /**
     * Groups tokens into visual rows based on vertical overlap.
     */
    fun clusterIntoRows(tokens: List<LayoutToken>): List<Row> {
        val rows = mutableListOf<Row>()
        var nextRowId = 0
        
        tokens.groupBy { it.pageIndex }.toSortedMap().forEach { (_, pageTokens) ->
            val normalLineHeight = estimateNormalLineHeight(pageTokens)
            val sorted = pageTokens.sortedWith(compareBy<LayoutToken>({ it.top }, { it.centerY }, { it.left }, { it.text }, { it.evidenceRef.blockIndex }, { it.evidenceRef.lineIndex }, { it.evidenceRef.elementIndex }))
            val deferredNumericTokens = normalLineHeight?.let {
                findAmbiguousNumericTokens(sorted, it)
            }.orEmpty()
            val baseRows = mutableListOf<MutableList<LayoutToken>>()
            var currentRowTokens = mutableListOf<LayoutToken>()
            
            for (token in sorted) {
                if (token in deferredNumericTokens) {
                    if (currentRowTokens.isNotEmpty()) baseRows.add(currentRowTokens)
                    baseRows.add(mutableListOf(token))
                    currentRowTokens = mutableListOf()
                    continue
                }
                if (currentRowTokens.isEmpty()) {
                    currentRowTokens.add(token)
                } else {
                    val rowTop = currentRowTokens.minOf { it.top }
                    val rowBottom = currentRowTokens.maxOf { it.bottom }
                    val rowHeight = rowBottom - rowTop
                    
                    val verticalOverlap = min(token.bottom, rowBottom) - max(token.top, rowTop)
                    val overlapRatio = if (rowHeight > 0) verticalOverlap / rowHeight else 0f
                    
                    // Same row if substantial vertical overlap or centers are very close
                    // A tiny epsilon keeps exactly half-height overlap in the structural
                    // candidate path, where competing rows and columns can be considered.
                    val sameRow = overlapRatio > 0.501f || abs(token.centerY - (rowTop + rowBottom) / 2) < LEGACY_CENTER_TOLERANCE
                    
                    if (sameRow) {
                        currentRowTokens.add(token)
                    } else {
                        baseRows.add(currentRowTokens)
                        currentRowTokens = mutableListOf(token)
                    }
                }
            }
            if (currentRowTokens.isNotEmpty()) {
                baseRows.add(currentRowTokens)
            }

            val mergedRows = if (normalLineHeight == null) baseRows else {
                mergeDisplacedComplementaryFields(baseRows, normalLineHeight)
            }
            mergedRows.forEach { rowTokens ->
                rows.add(Row(rowTokens.sortedWith(tokenOrder), nextRowId++))
            }
        }
        return rows.sortedWith(compareBy<Row>({ it.pageIndex }, { it.top }, { it.tokens.firstOrNull()?.left ?: 0f }))
    }

    private fun mergeDisplacedComplementaryFields(
        sourceRows: List<MutableList<LayoutToken>>,
        normalLineHeight: Float
    ): List<MutableList<LayoutToken>> {
        val rows = sourceRows.map { it.toMutableList() }.toMutableList()
        val establishedNumericColumns = inferEstablishedNumericColumns(sourceRows)
        // Rows are Y-sorted, so only a bounded neighborhood can be within one line height.
        // Descending removal keeps unvisited indices stable and makes this pass linear in N.
        for (movingIndex in rows.lastIndex downTo 0) {
            val moving = rows[movingIndex]
            // Move isolated numeric fields into stable descriptive bands. Keeping text
            // fragments stationary preserves conservative orphan/continuation behavior.
            if (moving.isEmpty() || !moving.all { isNumericField(it.text) }) continue
            val candidates = ((movingIndex - 3).coerceAtLeast(0)..(movingIndex + 3).coerceAtMost(rows.lastIndex))
                .asSequence()
                .filter { it != movingIndex }
                .mapNotNull { candidateIndex ->
                    associationScore(moving, rows[candidateIndex], normalLineHeight, establishedNumericColumns)
                        ?.let { Candidate(candidateIndex, it) }
                }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.rowIndex })
                .toList()
            val best = candidates.firstOrNull() ?: continue
            val runnerUp = candidates.getOrNull(1)
            if (runnerUp != null && best.score - runnerUp.score < AMBIGUITY_EPSILON) continue
            rows[best.rowIndex].addAll(moving)
            rows.removeAt(movingIndex)
        }
        return rows
    }

    private fun associationScore(
        fragment: List<LayoutToken>,
        candidate: List<LayoutToken>,
        lineHeight: Float,
        establishedNumericColumns: List<Float>
    ): Float? {
        val fragmentCenterY = centerY(fragment)
        val candidateCenterY = centerY(candidate)
        val delta = abs(fragmentCenterY - candidateCenterY)
        if (delta > lineHeight * MAX_CENTER_FACTOR || delta == 0f) return null

        // Adaptive merging is for complementary table fields, never arbitrary text continuation.
        val fragmentNumeric = fragment.all { isNumericField(it.text) }
        val candidateNumeric = candidate.all { isNumericField(it.text) }
        if (fragmentNumeric == candidateNumeric) return null

        val xConflict = fragment.any { token ->
            candidate.any { existing -> abs(token.centerX - existing.centerX) < X_BAND_TOLERANCE }
        }
        if (xConflict) return null

        val normalizedDistance = delta / lineHeight
        val columnEstablished = fragment.all { token ->
            establishedNumericColumns.any { center -> abs(token.centerX - center) < X_BAND_TOLERANCE }
        }
        // The wider half-to-one-line window is only a candidate window and requires
        // repeated X-column evidence. Stronger associations retain legacy resilience.
        if (normalizedDistance > STRONG_CENTER_FACTOR && !columnEstablished) return null
        
        val distanceScore = 1f - normalizedDistance
        val strongBonus = if (normalizedDistance <= STRONG_CENTER_FACTOR) 0.35f else 0f
        val establishedColumnBonus = if (columnEstablished) 0.15f else 0f
        return distanceScore + strongBonus + establishedColumnBonus
    }

    private fun inferEstablishedNumericColumns(rows: List<List<LayoutToken>>): List<Float> {
        val clusters = mutableListOf<MutableList<Pair<Int, LayoutToken>>>()
        rows.forEachIndexed { rowIndex, row ->
            row.filter { isNumericField(it.text) }.forEach { token ->
                val cluster = clusters.firstOrNull { items ->
                    abs(token.centerX - items.map { it.second.centerX }.average().toFloat()) < X_BAND_TOLERANCE
                }
                if (cluster == null) clusters.add(mutableListOf(rowIndex to token))
                else cluster.add(rowIndex to token)
            }
        }
        return clusters.filter { items -> items.map { it.first }.distinct().size >= 2 }
            .map { items -> items.map { it.second.centerX }.average().toFloat() }
    }

    private fun centerY(tokens: List<LayoutToken>): Float =
        tokens.map { it.centerY }.average().toFloat()

    private data class Candidate(val rowIndex: Int, val score: Float)

    /**
     * Isolates a numeric token only when the descriptive row it would otherwise join already
     * has a better-aligned value in the same X band. This leaves the displaced value for the
     * adaptive pass without slowing down or weakening ordinary aligned-row construction.
     */
    private fun findAmbiguousNumericTokens(
        tokens: List<LayoutToken>,
        normalLineHeight: Float
    ): Set<LayoutToken> {
        val byCenter = tokens.sortedWith(compareBy<LayoutToken>({ it.centerY }, { it.left }, { it.text }))
        val deferred = mutableSetOf<LayoutToken>()
        byCenter.forEachIndexed { index, token ->
            if (!isNumericField(token.text)) return@forEachIndexed
            val nearby = mutableListOf<LayoutToken>()
            var cursor = index - 1
            while (cursor >= 0 && token.centerY - byCenter[cursor].centerY <= normalLineHeight) {
                nearby.add(byCenter[cursor--])
            }
            cursor = index + 1
            while (cursor < byCenter.size && byCenter[cursor].centerY - token.centerY <= normalLineHeight) {
                nearby.add(byCenter[cursor++])
            }
            val nearbyDescriptions = nearby.filterNot { isNumericField(it.text) }
            if (nearbyDescriptions.size < 2) return@forEachIndexed
            val nearest = nearbyDescriptions.minBy { abs(it.centerY - token.centerY) }
            val tokenDistance = abs(nearest.centerY - token.centerY)
            val isAmbiguous = nearby.any { other ->
                isNumericField(other.text) &&
                other != token &&
                    abs(other.centerX - token.centerX) < X_BAND_TOLERANCE &&
                    abs(other.centerY - nearest.centerY) < tokenDistance
            }
            if (isAmbiguous) deferred.add(token)
        }
        return deferred
    }

    /** Page-local robust body-text height. The second median removes tiny artifacts and titles. */
    internal fun estimateNormalLineHeight(tokens: List<LayoutToken>): Float? {
        val heights = tokens.map { it.bottom - it.top }
            .filter { it.isFinite() && it > 0f && it <= 1f }
            .sorted()
        if (heights.size < 3) return null
        val initial = median(heights)
        val bodyHeights = heights.filter { it >= initial * 0.5f && it <= initial * 2f }
        if (bodyHeights.size < 3) return null
        return median(bodyHeights.sorted()).takeIf { it > 0f }
    }

    private fun median(values: List<Float>): Float {
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2f
    }

    internal fun isNumericField(text: String): Boolean = normalizeNumericField(text) != null

    /** Classification-only normalization; percentages deliberately remain non-monetary. */
    private fun normalizeNumericField(text: String): String? {
        var value = text.trim()
        if (value.isEmpty() || '%' in value) return null
        if (value.startsWith('(') && value.endsWith(')')) {
            value = "-" + value.substring(1, value.lastIndex).trim()
        } else if ('(' in value || ')' in value) {
            return null
        }
        value = value.replace(" ", "")
        if (!MONETARY_FIELD.matches(value)) return null
        return value.replace(CURRENCY_SYMBOL, "").replace(",", "")
            .takeIf { it.toBigDecimalOrNull() != null }
    }

    private val CURRENCY_SYMBOL = Regex("\\p{Sc}")
    private val MONETARY_FIELD = Regex(
        "^[+-]?(?:\\p{Sc})?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?:\\p{Sc})?$"
    )
    private val tokenOrder = compareBy<LayoutToken>({ it.left }, { it.centerY }, { it.text },
        { it.evidenceRef.blockIndex }, { it.evidenceRef.lineIndex }, { it.evidenceRef.elementIndex })

    /**
     * Clusters tokens by X position, ensuring support is based on distinct row IDs.
     */
    fun clusterTokensByX(tokens: List<LayoutToken>, rows: List<Row>): List<TokenCluster> {
        if (tokens.isEmpty()) return emptyList()
        
        // Map each token back to its rowId for support calculation
        val tokenToRowId = tokens.associateWith { token ->
            rows.find { row -> row.tokens.contains(token) }?.rowId ?: -1
        }

        val sorted = tokens.sortedBy { it.left }
        val clusters = mutableListOf<MutableList<LayoutToken>>()
        
        for (token in sorted) {
            val matchedCluster = clusters.find { cluster ->
                val avgLeft = cluster.map { it.left }.average().toFloat()
                abs(token.left - avgLeft) < 0.03f
            }
            if (matchedCluster != null) {
                matchedCluster.add(token)
            } else {
                clusters.add(mutableListOf(token))
            }
        }
        
        return clusters.map { c ->
            val distinctRowIds = c.mapNotNull { tokenToRowId[it] }.filter { it != -1 }.toSet()
            TokenCluster(
                tokens = c,
                avgLeft = c.map { it.left }.average().toFloat(),
                avgRight = c.map { it.right }.average().toFloat(),
                support = distinctRowIds.size,
                rowIds = distinctRowIds
            )
        }
    }
}
