package com.miara.cuentame.core.backup

import java.text.Normalizer

object ArchiveEntryValidator {

    /**
     * Validates that an archive entry name is relative, safe, canonical, and unique.
     * Prevents path traversal, absolute paths, and platform-specific escape sequences.
     */
    fun isSafe(name: String): Boolean {
        if (name.isBlank()) return false
        
        // 1. Basic character checks
        if (name.startsWith("/")) return false
        if (name.endsWith("/")) return false
        if (name.contains("\\")) return false
        
        // 2. Windows drive prefix rejection
        if (name.contains(":") && name.indexOf(":") < name.indexOf("/")) return false
        
        // 3. Segment validation
        val segments = name.split("/")
        for (segment in segments) {
            if (segment.isEmpty()) return false // repeated slashes or leading/trailing split result
            if (segment == "." || segment == "..") return false
            if (segment.all { it == ' ' }) return false
        }
        
        // 4. Canonical normalization check
        if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)) return false
        
        // 5. Length limit (UTF-8)
        val bytes = name.toByteArray(Charsets.UTF_8)
        if (bytes.size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) return false
        if (bytes.size != name.length && name.any { it.isISOControl() }) return false
        
        return true
    }
}
