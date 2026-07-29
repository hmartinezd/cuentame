package com.miara.cuentame.core.backup

/**
 * Single authoritative source for supported application locales.
 * Used in Settings UI, backup creation, and backup validation.
 */
object SupportedAppLocales {
    const val ENGLISH_US = "en-US"
    const val SPANISH_US = "es-US"

    val ALL: Set<String> = setOf(ENGLISH_US, SPANISH_US)
}
