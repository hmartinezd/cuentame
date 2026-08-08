package com.miara.cuentame.core.ocr.parser.matching

import com.miara.cuentame.core.common.text.normalizeName
import java.util.Locale

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
        return description.normalizeName()
    }

    /**
     * Normalizes package text (e.g. "25 LB CS").
     */
    fun normalizePackageText(packageText: String?): String {
        if (packageText == null) return ""
        return packageText.normalizeName()
    }
}
