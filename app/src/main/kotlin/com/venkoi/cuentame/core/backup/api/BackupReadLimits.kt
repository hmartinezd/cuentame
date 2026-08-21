package com.venkoi.cuentame.core.backup.api

import com.venkoi.cuentame.core.backup.BackupLimits

/**
 * Immutable limits for reading/inspecting a backup archive.
 * Reuses existing production limits where appropriate to ensure symmetry with the writer.
 */
data class BackupReadLimits(
    val maxEntryCount: Int = BackupLimits.MAX_ARCHIVE_ENTRY_COUNT,
    val maxManifestJsonBytes: Long = BackupLimits.MAX_MANIFEST_JSON_BYTES.toLong(),
    val maxSettingsJsonBytes: Long = BackupLimits.MAX_SETTINGS_JSON_BYTES.toLong(),
    val maxDatabaseJsonBytes: Long = BackupLimits.MAX_DATABASE_JSON_BYTES.toLong(),
    val maxChecksumsJsonBytes: Long = BackupLimits.MAX_CHECKSUMS_JSON_BYTES.toLong(),
    val maxAttachmentBytes: Long = 50 * 1024 * 1024L, // 50MB per attachment limit for restore safety
    val maxTotalUncompressedBytes: Long = BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES.toLong()
)
