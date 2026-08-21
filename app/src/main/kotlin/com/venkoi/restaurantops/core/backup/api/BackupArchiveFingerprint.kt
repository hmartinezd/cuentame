package com.venkoi.restaurantops.core.backup.api

/**
 * Stable logical identity of a backup archive.
 */
@JvmInline
value class BackupArchiveFingerprint(val value: String)
