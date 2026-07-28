package com.miara.cuentame.core.backup

/**
 * Shared safety limits for backup creation, validation, and testing.
 */
object BackupLimits {
    const val MAX_ENTRY_COUNT = 100
    const val MAX_ATTACHMENT_COUNT = 50
    const val MAX_ENTRY_NAME_LENGTH = 255
    const val MAX_SINGLE_JSON_BYTES = 10 * 1024 * 1024L // 10 MB
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 100 * 1024 * 1024L // 100 MB
}
