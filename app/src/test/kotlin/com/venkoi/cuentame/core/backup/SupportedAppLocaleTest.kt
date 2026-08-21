package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.model.locale.SupportedAppLocale
import org.junit.Test

class SupportedAppLocaleTest {

    @Test
    fun `languageTags contains all supported tags`() {
        assertThat(SupportedAppLocale.languageTags).containsExactly("en-US", "es-US")
    }

    @Test
    fun `fromLanguageTag returns correct enum for valid tags`() {
        assertThat(SupportedAppLocale.fromLanguageTag("en-US")).isEqualTo(SupportedAppLocale.ENGLISH_US)
        assertThat(SupportedAppLocale.fromLanguageTag("es-US")).isEqualTo(SupportedAppLocale.SPANISH_US)
    }

    @Test
    fun `fromLanguageTag returns null for invalid tags`() {
        assertThat(SupportedAppLocale.fromLanguageTag("fr-FR")).isNull()
        assertThat(SupportedAppLocale.fromLanguageTag("")).isNull()
    }
}
