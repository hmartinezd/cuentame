package com.venkoi.restaurantops.core.backup.api

import com.venkoi.restaurantops.core.backup.BackupLimits

/**
 * Immutable configuration for backup writing limits.
 */
data class BackupWriteLimits(
    val maxTotalUncompressedBytes: Long = BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES.toLong()
)
