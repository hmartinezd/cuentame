package com.venkoi.restaurantops.core.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvoiceNumberNormalizerTest {
    @Test fun `normalization is conservative and deterministic`() {
        assertEquals("AB-001/IX", InvoiceNumberNormalizer.normalize("  ab- 001 / IX "))
        assertEquals("O01I", InvoiceNumberNormalizer.normalize("o01i"))
        assertNull(InvoiceNumberNormalizer.normalize("  "))
    }
}
