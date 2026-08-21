package com.venkoi.restaurantops.core.backup

object BackupLimits {
    const val DATABASE_SCHEMA_VERSION_BASELINE = 2
    
    const val MAX_ARCHIVE_ENTRY_COUNT = 600
    const val MAX_ATTACHMENT_COUNT = 500
    const val MAX_ENTRY_NAME_LENGTH_BYTES = 255
    const val MAX_SINGLE_DOCUMENT_BYTES = 20 * 1024 * 1024 // 20MB
    
    // Per-JSON limits in bytes (UTF-8)
    const val MAX_MANIFEST_JSON_BYTES = 1 * 1024 * 1024 // 1MB
    const val MAX_SETTINGS_JSON_BYTES = 100 * 1024 // 100KB
    const val MAX_DATABASE_JSON_BYTES = 20 * 1024 * 1024 // 20MB
    const val MAX_CHECKSUMS_JSON_BYTES = 500 * 1024 // 500KB
    
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 500 * 1024 * 1024 // 500MB
}
