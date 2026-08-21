package com.venkoi.cuentame.core.backup.api

import com.venkoi.cuentame.core.backup.BackupLimits

/**
 * Immutable configuration for backup writing limits.
 */
data class BackupWriteLimits(
    val maxTotalUncompressedBytes: Long = BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES.toLong()
)
