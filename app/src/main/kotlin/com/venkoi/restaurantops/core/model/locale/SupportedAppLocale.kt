package com.venkoi.restaurantops.core.model.locale

/**
 * Pure shared definition for supported application locales.
 * Authoritative source for locale tags across restaurant profiles, settings,
 * backup creation, and backup validation.
 */
enum class SupportedAppLocale(
    val languageTag: String
) {
    ENGLISH_US("en-US"),
    SPANISH_US("es-US");

    companion object {
        val languageTags: Set<String> =
            entries.mapTo(linkedSetOf()) { it.languageTag }

        fun fromLanguageTag(
            value: String
        ): SupportedAppLocale? =
            entries.firstOrNull {
                it.languageTag == value
            }
    }
}
