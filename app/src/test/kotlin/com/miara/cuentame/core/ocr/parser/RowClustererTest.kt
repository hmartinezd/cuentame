package com.miara.cuentame.core.ocr.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class RowClustererTest {

    @Test
    fun `distinct row support - tokens in same row`() {
        val evidence = OcrEvidenceRef(0, 0, 0, 0)
        // One visual row with three numeric tokens near same X
        val tokens = listOf(
            LayoutToken("10.00", 0, 0.80f, 0.10f, 0.85f, 0.12f, evidence),
            LayoutToken("20.00", 0, 0.81f, 0.10f, 0.86f, 0.12f, evidence),
            LayoutToken("30.00", 0, 0.79f, 0.10f, 0.84f, 0.12f, evidence)
        )
        
        val rows = RowClusterer.clusterIntoRows(tokens)
        assertEquals(1, rows.size)
        
        val clusters = RowClusterer.clusterTokensByX(tokens, rows)
        assertEquals(1, clusters.size)
        assertEquals(1, clusters[0].support) // Only 1 distinct row
    }

    @Test
    fun `distinct row support - tokens in different rows`() {
        val evidence = OcrEvidenceRef(0, 0, 0, 0)
        // Three different rows with numeric tokens at similar X
        val tokens = listOf(
            LayoutToken("10.00", 0, 0.80f, 0.10f, 0.85f, 0.12f, evidence),
            LayoutToken("20.00", 0, 0.81f, 0.20f, 0.86f, 0.22f, evidence),
            LayoutToken("30.00", 0, 0.79f, 0.30f, 0.84f, 0.32f, evidence)
        )
        
        val rows = RowClusterer.clusterIntoRows(tokens)
        assertEquals(3, rows.size)
        
        val clusters = RowClusterer.clusterTokensByX(tokens, rows)
        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].support) // 3 distinct rows
    }

    @Test
    fun `row reconstruction is invariant to token input order`() {
        val evidence = OcrEvidenceRef(0, 0, 0, 0)
        val tokens = listOf(
            LayoutToken("A", 0, .1f, .1f, .2f, .12f, evidence),
            LayoutToken("10.00", 0, .8f, .101f, .9f, .121f, evidence),
            LayoutToken("B", 0, .1f, .2f, .2f, .22f, evidence),
            LayoutToken("20.00", 0, .8f, .201f, .9f, .221f, evidence)
        )
        val expected = RowClusterer.clusterIntoRows(tokens).map(Row::text)
        assertEquals(expected, RowClusterer.clusterIntoRows(tokens.reversed()).map(Row::text))
        assertEquals(expected, RowClusterer.clusterIntoRows(listOf(tokens[2], tokens[0], tokens[3], tokens[1])).map(Row::text))
    }
}
