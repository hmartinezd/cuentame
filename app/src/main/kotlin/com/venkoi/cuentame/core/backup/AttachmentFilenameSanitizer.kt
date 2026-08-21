package com.venkoi.cuentame.core.backup

object AttachmentFilenameSanitizer {

    private const val MAX_FILENAME_LENGTH = 128

    /**
     * Sanitizes a filename for use within the backup archive.
     * Removes path separators, control characters, and prevents traversal.
     */
    fun sanitize(originalName: String?): String {
        if (originalName.isNullOrBlank()) return "attachment"

        // 1. Remove path separators and null bytes
        var sanitized = originalName.replace("/", "_")
            .replace("\\", "_")
            .replace("\u0000", "")

        // 2. Remove control characters
        sanitized = sanitized.replace(Regex("[\\x00-\\x1f\\x7f]"), "")

        // 3. Remove common illegal characters for filesystems just in case
        sanitized = sanitized.replace(Regex("[<>:\"|?*]"), "_")

        // 4. Collapse repeated whitespace and underscores
        sanitized = sanitized.replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim()

        // 5. Avoid blank, . and .. and variants
        if (sanitized.isBlank() || sanitized.all { it == '.' }) {
            return "attachment"
        }

        // 6. Enforce maximum length while trying to preserve extension
        if (sanitized.length > MAX_FILENAME_LENGTH) {
            val extensionIndex = sanitized.lastIndexOf('.')
            if (extensionIndex != -1 && sanitized.length - extensionIndex < 10) {
                val extension = sanitized.substring(extensionIndex)
                val base = sanitized.substring(0, MAX_FILENAME_LENGTH - extension.length)
                sanitized = base + extension
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH)
            }
        }

        return sanitized
    }

    /**
     * Rejects filenames that are unsafe or malformed during validation.
     */
    fun isValid(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("/") || name.contains("\\") || name.contains("\u0000")) return false
        if (name.contains(Regex("[\\x00-\\x1f\\x7f]"))) return false
        if (name == "." || name == "..") return false
        if (name.length > MAX_FILENAME_LENGTH) return false
        return true
    }
}
