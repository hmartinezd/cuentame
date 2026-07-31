package com.miara.cuentame.feature.settings.presentation

import androidx.annotation.StringRes
import com.miara.cuentame.R
import com.miara.cuentame.core.model.backup.BackupRestoreFailure

/**
 * Pure mapper from domain [BackupRestoreFailure] to localized string resource IDs.
 * Ensures no technical details (enum names, paths, checksums) are leaked to the UI.
 */
@StringRes
fun BackupRestoreFailure.toUserMessageRes(): Int {
    return when (this) {
        BackupRestoreFailure.InvalidZip -> R.string.restore_error_invalid_zip
        BackupRestoreFailure.MissingCoreEntry -> R.string.restore_error_missing_core
        BackupRestoreFailure.ChecksumMismatch,
        BackupRestoreFailure.ManifestMismatch,
        BackupRestoreFailure.AttachmentMismatch -> R.string.restore_error_checksum
        
        BackupRestoreFailure.UnsupportedFormatVersion,
        BackupRestoreFailure.IncompatibleSchemaVersion -> R.string.restore_error_incompatible
        
        BackupRestoreFailure.EntryLimitExceeded,
        BackupRestoreFailure.TotalLimitExceeded -> R.string.restore_error_limit
        
        is BackupRestoreFailure.SnapshotIntegrityFailure -> R.string.restore_error_integrity_generic
        
        BackupRestoreFailure.SourceUnavailable -> R.string.backup_error_destination
        BackupRestoreFailure.PermissionDenied -> R.string.backup_error_permission
        
        BackupRestoreFailure.DuplicateEntry,
        BackupRestoreFailure.UnsafeEntryPath,
        BackupRestoreFailure.UnexpectedEntry,
        BackupRestoreFailure.MalformedChecksums,
        BackupRestoreFailure.MalformedSnapshot,
        BackupRestoreFailure.MalformedPreferences,
        BackupRestoreFailure.MalformedManifest -> R.string.restore_error_checksum // Group technical structure errors

        BackupRestoreFailure.GenericIo,
        BackupRestoreFailure.OperationInterrupted -> R.string.error_generic
        
        BackupRestoreFailure.InspectionExpired -> R.string.restore_error_checksum
        BackupRestoreFailure.RollbackFailed,
        BackupRestoreFailure.RecoveryRequired -> R.string.restore_recovery_required_title
    }
}
