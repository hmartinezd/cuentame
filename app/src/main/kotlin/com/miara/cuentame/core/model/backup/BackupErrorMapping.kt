package com.miara.cuentame.core.model.backup

import com.miara.cuentame.R

fun BackupResult.Error.toUserMessageRes(): Int = when (this) {
    is BackupResult.Error.DestinationUnavailable -> R.string.backup_error_destination
    is BackupResult.Error.PermissionDenied -> R.string.backup_error_permission
    is BackupResult.Error.InsufficientStorage -> R.string.backup_error_storage
    is BackupResult.Error.SerializationFailure -> R.string.backup_error_serialization
    is BackupResult.Error.DatabaseSnapshotFailure -> R.string.backup_error_database
    is BackupResult.Error.MissingAttachment -> R.string.backup_error_attachment_missing
    is BackupResult.Error.UnreadableAttachment -> R.string.backup_error_attachment_unreadable
    is BackupResult.Error.ChecksumFailure -> R.string.backup_error_checksum
    is BackupResult.Error.ArchiveValidationFailure -> R.string.backup_error_validation
    is BackupResult.Error.UnsupportedPersistentData -> R.string.backup_error_unsupported
    is BackupResult.Error.OperationCancelled -> R.string.backup_cancelled_message
    is BackupResult.Error.UnexpectedInternalFailure -> R.string.backup_error_unknown
    is BackupResult.Error.Unknown -> R.string.backup_error_unknown
}
