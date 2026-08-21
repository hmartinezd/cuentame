package com.venkoi.cuentame.core.ocr.parser.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryNormalizationTest {

    @Test
    fun `normalizeVendorCode preserves leading zeros`() {
        assertEquals("000123", InventoryNormalization.normalizeVendorCode(" 000123 "))
        assertEquals("001247", InventoryNormalization.normalizeVendorCode("001247"))
    }

    @Test
    fun `normalizeVendorCode is case-insensitive uppercase`() {
        assertEquals("ABC-123", InventoryNormalization.normalizeVendorCode("abc-123"))
    }

    @Test
    fun `normalizeVendorCode preserves meaningful punctuation`() {
        assertEquals("ABC-123", InventoryNormalization.normalizeVendorCode("ABC-123"))
        assertEquals("ABC/123", InventoryNormalization.normalizeVendorCode("ABC/123"))
        assertEquals("ABC.123", InventoryNormalization.normalizeVendorCode("ABC.123"))
        assertEquals("ABC 123", InventoryNormalization.normalizeVendorCode("ABC  123"))
    }

    @Test
    fun `normalizeDescription uses standard name normalization`() {
        // normalizeName: trim, collapse spaces, lowercase
        assertEquals("tomato roma", InventoryNormalization.normalizeDescription(" TOMATO  ROMA "))
    }

    @Test
    fun `normalizePackageText uses standard name normalization`() {
        assertEquals("25 lb cs", InventoryNormalization.normalizePackageText(" 25 LB  CS "))
    }

    @Test
    fun `package identity normalizes safe spacing variations`() {
        assertEquals(InventoryNormalization.normalizePackageText("40LB"), InventoryNormalization.normalizePackageText("40 LB"))
        assertEquals(InventoryNormalization.normalizePackageText("4X1 GAL"), InventoryNormalization.normalizePackageText("4 X 1 GAL"))
        assertEquals(InventoryNormalization.normalizePackageText("6/5 LB"), InventoryNormalization.normalizePackageText("6 / 5 LB"))
        assertEquals(InventoryNormalization.normalizePackageText("2/10LB"), InventoryNormalization.normalizePackageText("2 / 10 LB"))
    }
}
