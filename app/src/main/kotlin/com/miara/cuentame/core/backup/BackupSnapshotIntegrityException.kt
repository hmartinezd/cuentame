package com.miara.cuentame.core.backup

/**
 * Thrown by [BackupSnapshotIntegrityValidator] when snapshot logical integrity fails.
 * Always carries a stable [code] for programmatic assertions in tests.
 * Human-readable [message] must not include customer data, URIs, or raw JSON.
 */
class BackupSnapshotIntegrityException(
    val code: BackupSnapshotIntegrityCode,
    message: String,
) : IllegalStateException(message)
