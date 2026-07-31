package com.miara.cuentame.core.model.backup

import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode

/**
 * Domain failures for the backup restore inspection and application process.
 */
sealed interface BackupRestoreFailure {
    data object SourceUnavailable : BackupRestoreFailure
    data object PermissionDenied : BackupRestoreFailure
    data object InvalidZip : BackupRestoreFailure
    data object MissingCoreEntry : BackupRestoreFailure
    data object DuplicateEntry : BackupRestoreFailure
    data object UnexpectedEntry : BackupRestoreFailure
    data object UnsafeEntryPath : BackupRestoreFailure
    data object EntryLimitExceeded : BackupRestoreFailure
    data object TotalLimitExceeded : BackupRestoreFailure
    data object MalformedChecksums : BackupRestoreFailure
    data object ChecksumMismatch : BackupRestoreFailure
    data object MalformedSnapshot : BackupRestoreFailure
    data object MalformedPreferences : BackupRestoreFailure
    data object MalformedManifest : BackupRestoreFailure
    data object UnsupportedFormatVersion : BackupRestoreFailure
    data object IncompatibleSchemaVersion : BackupRestoreFailure
    data object ManifestMismatch : BackupRestoreFailure
    data class SnapshotIntegrityFailure(val code: BackupSnapshotIntegrityCode) : BackupRestoreFailure
    data object AttachmentMismatch : BackupRestoreFailure
    data object AttachmentsNotSupported : BackupRestoreFailure
    data object DatabaseRestoreFailed : BackupRestoreFailure
    data object PreferencesRestoreFailed : BackupRestoreFailure
    data object RestorePreparationFailed : BackupRestoreFailure
    data object FinalVerificationFailed : BackupRestoreFailure
    data object GenericIo : BackupRestoreFailure
    data object OperationInterrupted : BackupRestoreFailure
    data object InspectionExpired : BackupRestoreFailure
    data object RollbackFailed : BackupRestoreFailure
    data object RecoveryRequired : BackupRestoreFailure
}
