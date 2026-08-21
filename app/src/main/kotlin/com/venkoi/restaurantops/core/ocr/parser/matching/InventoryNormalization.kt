package com.venkoi.restaurantops.core.ocr.parser.matching

import com.venkoi.restaurantops.core.common.text.normalizeName
import java.util.Locale
import java.text.Normalizer

object InventoryNormalization {

    /**
     * Conservative normalization for vendor codes.
     * Preserves leading zeros and meaningful punctuation.
     */
    fun normalizeVendorCode(code: String?): String {
        if (code == null) return ""
        return code.trim()
            .uppercase(Locale.ROOT)
            .replace("\\s+".toRegex(), " ")
    }

    /**
     * Normalizes item descriptions for matching.
     */
    fun normalizeDescription(description: String?): String {
        if (description == null) return ""
        return Normalizer.normalize(description, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .normalizeName()
    }

    /**
     * Normalizes package text (e.g. "25 LB CS") for better matching.
     * Uses a conservative set of aliases to bridge common OCR/Vendor variations.
     */
    fun normalizePackageText(packageText: String?): String {
        if (packageText == null) return ""
        val normalized = packageText
            // These separators describe package identity, not package arithmetic.
            .replace("\\s*([Xx/])\\s*".toRegex(), " $1 ")
            .replace("(?<=\\d)(?=\\p{L})|(?<=\\p{L})(?=\\d)".toRegex(), " ")
            .normalizeName()
        
        // Conservative aliases for matching
        return normalized.split(" ").joinToString(" ") { token ->
            when (token) {
                "lbs" -> "lb"
                "gallon" -> "gal"
                else -> token
            }
        }
    }
}
