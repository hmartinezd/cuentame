package com.miara.cuentame.core.ocr.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageTextDetectorTest {

    @Test
    fun `detect standard weight`() {
        assertEquals("25 LB", PackageTextDetector.detectPackageText("25 LB"))
        assertEquals("50 LB", PackageTextDetector.detectPackageText("50 LB"))
        assertEquals("25 LB CS", PackageTextDetector.detectPackageText("25 LB CS"))
    }

    @Test
    fun `detect case quantity`() {
        assertEquals("6/5 LB", PackageTextDetector.detectPackageText("6/5 LB"))
        assertEquals("2/10 LB", PackageTextDetector.detectPackageText("2/10 LB"))
    }

    @Test
    fun `detect count`() {
        assertEquals("12 CT", PackageTextDetector.detectPackageText("12 CT"))
        assertEquals("24 CT", PackageTextDetector.detectPackageText("24 CT"))
    }

    @Test
    fun `detect multiplier`() {
        assertEquals("4 X 1 GAL", PackageTextDetector.detectPackageText("4 X 1 GAL"))
        assertEquals("4X1 GAL", PackageTextDetector.detectPackageText("4X1 GAL"))
    }

    @Test
    fun `detect standalone units`() {
        assertEquals("CASE", PackageTextDetector.detectPackageText("CASE"))
        assertEquals("CS", PackageTextDetector.detectPackageText("CS"))
        assertEquals("EA", PackageTextDetector.detectPackageText("EA"))
        assertEquals("GAL", PackageTextDetector.detectPackageText("GAL"))
    }

    @Test
    fun `do not detect generic text`() {
        assertNull(PackageTextDetector.detectPackageText("TOMATO ROMA"))
        assertNull(PackageTextDetector.detectPackageText("INVOICE"))
        assertNull(PackageTextDetector.detectPackageText("12345"))
    }
}
