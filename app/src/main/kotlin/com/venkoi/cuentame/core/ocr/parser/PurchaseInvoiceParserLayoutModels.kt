package com.venkoi.cuentame.core.ocr.parser

/**
 * Internal layout token used during geometric analysis.
 * Not to be confused with raw OCR tokens.
 */
data class LayoutToken(
    val text: String,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val evidenceRef: OcrEvidenceRef,
    val ocrConfidence: Float? = null
) {
    val centerX: Float get() = left + (right - left) / 2
    val centerY: Float get() = top + (bottom - top) / 2
}

data class DocumentPosition(val pageIndex: Int, val top: Float) : Comparable<DocumentPosition> {
    override fun compareTo(other: DocumentPosition): Int =
        compareValuesBy(this, other, DocumentPosition::pageIndex, DocumentPosition::top)
}

/**
 * Represents a logical visual row on a page.
 */
data class Row(
    val tokens: List<LayoutToken>,
    val rowId: Int = -1
) {
    val pageIndex: Int = tokens.first().pageIndex
    val top: Float = tokens.minOf { it.top }
    val bottom: Float = tokens.maxOf { it.bottom }
    val text: String = tokens.joinToString(" ") { it.text }
}

/**
 * A cluster of tokens aligned by X position.
 * Support is based on the number of DISTINCT visual rows participating.
 */
data class TokenCluster(
    val tokens: List<LayoutToken>,
    val avgLeft: Float,
    val avgRight: Float,
    val support: Int,
    val rowIds: Set<Int>
) {
    fun toRange(tolerance: Float = 0.02f) = FloatRange(avgLeft - tolerance, avgRight + tolerance)
}

data class FloatRange(val start: Float, val end: Float) {
    fun contains(v: Float) = v in start..end
}
