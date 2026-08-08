package com.miara.cuentame.core.ocr.parser

/**
 * Detects package/size information in OCR tokens.
 */
object PackageTextDetector {

    private val PACKAGE_PATTERNS = listOf(
        Regex("\\b\\d+\\s*X\\s*\\d+\\s*(LB|KG|OZ|GAL|CT|PK|BX|BAG|CASE|CS|EA)?\\b", RegexOption.IGNORE_CASE),
        Regex("(\\b\\d+/)?\\d+\\s*(LB|KG|OZ|GAL|CT|PK|BX|BAG|CASE|CS|EA)(\\s+(CS|EA|PK|BX|BAG|CASE))?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d+\\s*(LB|KG|OZ|GAL|CT|PK|BX|BAG|CASE|CS|EA)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(CASE|CS|EA|PK|BX|BAG|GAL|OZ|LB|KG)\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * Extracts package text from a token if it matches known patterns.
     */
    fun detectPackageText(text: String): String? {
        for (pattern in PACKAGE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                return match.value.trim()
            }
        }
        return null
    }

    /**
     * Attempts to find a package-like token in a list of tokens.
     */
    fun findPackageToken(tokens: List<String>): String? {
        return tokens.mapNotNull { detectPackageText(it) }.firstOrNull()
    }
}
