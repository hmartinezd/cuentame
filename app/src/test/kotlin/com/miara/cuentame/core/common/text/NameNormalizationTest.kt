package com.miara.cuentame.core.common.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameNormalizationTest {
    @Test
    fun `normalizeName trims and collapses spaces`() {
        assertThat("  Chicken   Breast ".normalizeName()).isEqualTo("chicken breast")
    }

    @Test
    fun `normalizeName lowercases root locale`() {
        assertThat("Walk-In Cooler".normalizeName()).isEqualTo("walk-in cooler")
    }

    @Test
    fun `normalizeName handles empty string`() {
        assertThat("".normalizeName()).isEqualTo("")
    }
}
