package com.miara.cuentame.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miara.cuentame.R
import com.miara.cuentame.core.model.backup.BackupRestoreFailure

@Composable
fun BackupRestoreFailure.toUserMessage(): String {
    val messageRes = when (this) {
        BackupRestoreFailure.InvalidZip -> R.string.restore_error_invalid_zip
        BackupRestoreFailure.MissingCoreEntry -> R.string.restore_error_missing_core
        BackupRestoreFailure.ChecksumMismatch -> R.string.restore_error_checksum
        BackupRestoreFailure.UnsupportedFormatVersion,
        BackupRestoreFailure.IncompatibleSchemaVersion -> R.string.restore_error_incompatible
        BackupRestoreFailure.EntryLimitExceeded,
        BackupRestoreFailure.TotalLimitExceeded -> R.string.restore_error_limit
        is BackupRestoreFailure.SnapshotIntegrityFailure -> R.string.restore_error_integrity_generic
        BackupRestoreFailure.SourceUnavailable -> R.string.backup_error_destination
        BackupRestoreFailure.PermissionDenied -> R.string.backup_error_permission
        BackupRestoreFailure.ManifestMismatch,
        BackupRestoreFailure.AttachmentMismatch -> R.string.restore_error_checksum
        else -> R.string.error_generic
    }
    
    return if (this is BackupRestoreFailure.SnapshotIntegrityFailure) {
        // We use a generic message for integrity instead of exposing code.name
        stringResource(messageRes)
    } else {
        stringResource(messageRes)
    }
}
