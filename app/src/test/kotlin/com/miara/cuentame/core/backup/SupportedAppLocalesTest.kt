package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [SupportedAppLocales].
 */
class SupportedAppLocalesTest {

    @Test
    fun `ALL contains en-US and es-US`() {
        assertThat(SupportedAppLocales.ALL).containsExactly(
            SupportedAppLocales.ENGLISH_US,
            SupportedAppLocales.SPANISH_US
        )
    }

    @Test
    fun `ENGLISH_US is exactly en-US`() {
        assertThat(SupportedAppLocales.ENGLISH_US).isEqualTo("en-US")
    }

    @Test
    fun `SPANISH_US is exactly es-US`() {
        assertThat(SupportedAppLocales.SPANISH_US).isEqualTo("es-US")
    }

    @Test
    fun `generic BCP47 tags are not in ALL`() {
        assertThat(SupportedAppLocales.ALL).doesNotContain("en")
        assertThat(SupportedAppLocales.ALL).doesNotContain("es")
        assertThat(SupportedAppLocales.ALL).doesNotContain("fr-FR")
    }

    @Test
    fun `ALL contains exactly 2 locales`() {
        assertThat(SupportedAppLocales.ALL).hasSize(2)
    }
}
