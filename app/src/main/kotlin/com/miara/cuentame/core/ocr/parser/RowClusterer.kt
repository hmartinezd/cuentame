package com.miara.cuentame.core.ocr.parser

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles clustering tokens into visual rows and X-position clusters.
 */
object RowClusterer {

    /**
     * Groups tokens into visual rows based on vertical overlap.
     */
    fun clusterIntoRows(tokens: List<LayoutToken>): List<Row> {
        val rows = mutableListOf<Row>()
        var nextRowId = 0
        
        tokens.groupBy { it.pageIndex }.toSortedMap().forEach { (_, pageTokens) ->
            val sorted = pageTokens.sortedWith(compareBy<LayoutToken>({ it.top }, { it.centerY }, { it.left }, { it.text }, { it.evidenceRef.blockIndex }, { it.evidenceRef.lineIndex }, { it.evidenceRef.elementIndex }))
            var currentRowTokens = mutableListOf<LayoutToken>()
            
            for (token in sorted) {
                if (currentRowTokens.isEmpty()) {
                    currentRowTokens.add(token)
                } else {
                    val rowTop = currentRowTokens.minOf { it.top }
                    val rowBottom = currentRowTokens.maxOf { it.bottom }
                    val rowHeight = rowBottom - rowTop
                    
                    val verticalOverlap = min(token.bottom, rowBottom) - max(token.top, rowTop)
                    val overlapRatio = if (rowHeight > 0) verticalOverlap / rowHeight else 0f
                    
                    // Same row if substantial vertical overlap or centers are very close
                    val sameRow = overlapRatio > 0.5f || abs((token.top + token.bottom)/2 - (rowTop + rowBottom)/2) < 0.005f
                    
                    if (sameRow) {
                        currentRowTokens.add(token)
                    } else {
                        rows.add(Row(currentRowTokens.sortedBy { it.left }, nextRowId++))
                        currentRowTokens = mutableListOf(token)
                    }
                }
            }
            if (currentRowTokens.isNotEmpty()) {
                rows.add(Row(currentRowTokens.sortedBy { it.left }, nextRowId++))
            }
        }
        return rows.sortedWith(compareBy<Row>({ it.pageIndex }, { it.top }, { it.tokens.firstOrNull()?.left ?: 0f }))
    }

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
