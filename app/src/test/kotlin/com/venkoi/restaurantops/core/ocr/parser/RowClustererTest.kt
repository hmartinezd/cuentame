package com.venkoi.restaurantops.core.ocr.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `small and half-line displaced amount joins description`() {
        listOf(.107f, .110f).forEach { amountCenter ->
            val tokens = listOf(
                token("CHICKEN", .2f, .100f), token("18.99", .85f, amountCenter),
                token("RICE", .2f, .200f), token("22.50", .85f, .200f)
            )
            val rows = RowClusterer.clusterIntoRows(tokens)
            assertTrue(rows.any { it.text == "CHICKEN 18.99" })
        }
    }

    @Test
    fun `larger displacement uses established amount column and missing field`() {
        val tokens = listOf(
            token("CHICKEN", .2f, .100f), token("18.99", .85f, .115f),
            token("RICE", .2f, .120f), token("22.50", .85f, .120f)
        )
        val rows = RowClusterer.clusterIntoRows(tokens)
        assertEquals(listOf("CHICKEN 18.99", "RICE 22.50"), rows.map(Row::text))
    }

    @Test
    fun `greedy next-row regression is invariant to token and block order`() {
        val fixtures = listOf(
            listOf(
                token("CHICKEN", .2f, .100f, block = 0), token("18.99", .85f, .115f, block = 1),
                token("RICE", .2f, .120f, block = 0), token("22.50", .85f, .120f, block = 1)
            ),
            listOf(
                token("CHICKEN", .2f, .100f, block = 1), token("18.99", .85f, .115f, block = 0),
                token("RICE", .2f, .120f, block = 1), token("22.50", .85f, .120f, block = 0)
            )
        )
        val expected = listOf("CHICKEN 18.99", "RICE 22.50")
        fixtures.forEach { tokens ->
            assertEquals(expected, RowClusterer.clusterIntoRows(tokens).map(Row::text))
            assertEquals(expected, RowClusterer.clusterIntoRows(tokens.reversed()).map(Row::text))
            assertEquals(expected, RowClusterer.clusterIntoRows(listOf(tokens[2], tokens[0], tokens[3], tokens[1])).map(Row::text))
        }
    }

    @Test
    fun `ordinary aligned amount forms its row directly`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("CHICKEN", .2f, .100f), token("18.99", .85f, .101f),
            token("padding", .2f, .200f)
        ))
        assertTrue(rows.any { it.text == "CHICKEN 18.99" })
    }

    @Test
    fun `monetary field classification handles currency and excludes percentages`() {
        listOf("18.99", "1,244.26", "$18.99", "$1,244.26", "-4.30", "-$4.30", "($4.30)",
            "€18.99", "18.99£").forEach { value ->
            assertTrue("Expected monetary field: $value", RowClusterer.isNumericField(value))
        }
        listOf("7.5%", "10%", "$", "plastic bag").forEach { value ->
            assertTrue("Expected non-monetary field: $value", !RowClusterer.isNumericField(value))
        }
    }

    @Test
    fun `adjacent products one line apart remain separate`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("Chicken", .2f, .100f), token("10.00", .85f, .100f),
            token("Rice", .2f, .120f), token("5.00", .85f, .120f)
        ))
        assertEquals(listOf("Chicken 10.00", "Rice 5.00"), rows.map(Row::text))
    }

    @Test
    fun `midpoint amount stays unattached when competing rows are ambiguous`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("Product A", .2f, .100f), token("12.34", .85f, .110f), token("Product B", .2f, .120f)
        ))
        assertEquals(3, rows.size)
        assertTrue(rows.any { it.text == "12.34" })
    }

    @Test
    fun `occupied amount column resolves midpoint competition`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("Product A", .2f, .100f), token("12.34", .85f, .110f),
            token("Product B", .2f, .120f), token("56.78", .85f, .120f)
        ))
        assertEquals(listOf("Product A 12.34", "Product B 56.78"), rows.map(Row::text))
    }

    @Test
    fun `text orphan is not adaptively merged`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("Chicago Italian Bread", .2f, .100f), token("13.29", .85f, .100f),
            token("plastic bag", .3f, .115f),
            token("French Bread", .2f, .130f), token("2.49", .85f, .130f)
        ))
        assertTrue(rows.any { it.text == "plastic bag" })
        assertEquals(3, rows.size)
    }

    @Test
    fun `tokens on different pages never associate`() {
        val rows = RowClusterer.clusterIntoRows(listOf(
            token("Bottom", .2f, .900f, page = 0), token("10.00", .85f, .910f, page = 1),
            token("Top", .2f, .900f, page = 1), token("20.00", .85f, .950f, page = 1)
        ))
        assertTrue(rows.none { row -> row.tokens.map { it.pageIndex }.distinct().size > 1 })
    }

    @Test
    fun `line height median rejects title and tiny artifact`() {
        val tokens = listOf(
            token("TITLE", .2f, .10f, height = .10f), token("dust", .2f, .20f, height = .002f),
            token("A", .2f, .30f), token("B", .2f, .34f), token("C", .2f, .38f), token("D", .2f, .42f)
        )
        assertEquals(.02f, RowClusterer.estimateNormalLineHeight(tokens)!!, .0001f)
    }

    @Test
    fun `cross-block displacement is invariant to block and input order`() {
        val descriptions = listOf(
            token("Chicken", .2f, .100f, block = 1), token("Rice", .2f, .140f, block = 1)
        )
        val amounts = listOf(
            token("10.00", .85f, .111f, block = 0), token("5.00", .85f, .151f, block = 0)
        )
        val expected = listOf("Chicken 10.00", "Rice 5.00")
        assertEquals(expected, RowClusterer.clusterIntoRows(descriptions + amounts).map(Row::text))
        assertEquals(expected, RowClusterer.clusterIntoRows((amounts + descriptions).reversed()).map(Row::text))
    }

    private fun token(
        text: String,
        centerX: Float,
        centerY: Float,
        height: Float = .02f,
        page: Int = 0,
        block: Int = 0
    ) = LayoutToken(
        text, page, centerX - .05f, centerY - height / 2, centerX + .05f, centerY + height / 2,
        OcrEvidenceRef(page, block, 0, 0)
    )
}
