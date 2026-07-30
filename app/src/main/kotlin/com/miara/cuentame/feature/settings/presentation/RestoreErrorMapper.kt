package com.miara.cuentame.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miara.cuentame.R
import com.miara.cuentame.core.model.backup.BackupRestoreFailure

@Composable
fun BackupRestoreFailure.toUserMessage(): String {
    return when (this) {
        BackupRestoreFailure.InvalidZip -> stringResource(R.string.restore_error_invalid_zip)
        BackupRestoreFailure.MissingCoreEntry -> stringResource(R.string.restore_error_missing_core)
        BackupRestoreFailure.ChecksumMismatch -> stringResource(R.string.restore_error_checksum)
        BackupRestoreFailure.UnsupportedFormatVersion,
        BackupRestoreFailure.IncompatibleSchemaVersion -> stringResource(R.string.restore_error_incompatible)
        BackupRestoreFailure.EntryLimitExceeded,
        BackupRestoreFailure.TotalLimitExceeded -> stringResource(R.string.restore_error_limit)
        is BackupRestoreFailure.SnapshotIntegrityFailure -> {
            // Group granular integrity codes into a simpler user message
            stringResource(R.string.restore_error_integrity, this.code.name)
        }
        BackupRestoreFailure.SourceUnavailable -> stringResource(R.string.backup_error_destination)
        BackupRestoreFailure.PermissionDenied -> stringResource(R.string.backup_error_permission)
        else -> stringResource(R.string.error_generic)
    }
}
