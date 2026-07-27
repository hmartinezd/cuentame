package com.miara.cuentame.core.backup

object ArchiveEntryValidator {

    /**
     * Validates that an archive entry name is relative, safe, and unique.
     * Prevents path traversal and absolute paths.
     */
    fun isSafe(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith("/")) return false
        if (name.contains("\\")) return false
        
        val segments = name.split("/")
        for (segment in segments) {
            if (segment == "." || segment == "..") return false
        }
        
        return true
    }

    fun sanitize(name: String): String {
        return name.replace("\\", "/")
            .trimStart('/')
            .replace(Regex("/\\./"), "/")
            .replace(Regex("/+"), "/")
    }
}
