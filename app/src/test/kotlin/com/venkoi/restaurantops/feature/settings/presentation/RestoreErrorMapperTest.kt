package com.venkoi.restaurantops.feature.settings.presentation

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.model.backup.BackupRestoreFailure
import com.venkoi.restaurantops.core.backup.BackupSnapshotIntegrityCode
import org.junit.Test

class RestoreErrorMapperTest {

    @Test
    fun `restore failure mappings are correct`() {
        val mappings = mapOf(
            BackupRestoreFailure.InvalidZip to R.string.restore_error_invalid_zip,
            BackupRestoreFailure.MissingCoreEntry to R.string.restore_error_missing_core,
            BackupRestoreFailure.ChecksumMismatch to R.string.restore_error_checksum,
            BackupRestoreFailure.ManifestMismatch to R.string.restore_error_checksum,
            BackupRestoreFailure.AttachmentMismatch to R.string.restore_error_checksum,
            BackupRestoreFailure.UnsupportedFormatVersion to R.string.restore_error_incompatible,
            BackupRestoreFailure.IncompatibleSchemaVersion to R.string.restore_error_incompatible,
            BackupRestoreFailure.EntryLimitExceeded to R.string.restore_error_limit,
            BackupRestoreFailure.TotalLimitExceeded to R.string.restore_error_limit,
            BackupRestoreFailure.SourceUnavailable to R.string.backup_error_destination,
            BackupRestoreFailure.PermissionDenied to R.string.backup_error_permission,
            BackupRestoreFailure.GenericIo to R.string.error_generic,
            BackupRestoreFailure.OperationInterrupted to R.string.error_generic,
            BackupRestoreFailure.DuplicateEntry to R.string.restore_error_checksum,
            BackupRestoreFailure.UnsafeEntryPath to R.string.restore_error_checksum,
            BackupRestoreFailure.UnexpectedEntry to R.string.restore_error_checksum,
            BackupRestoreFailure.MalformedChecksums to R.string.restore_error_checksum,
            BackupRestoreFailure.MalformedSnapshot to R.string.restore_error_checksum,
            BackupRestoreFailure.MalformedPreferences to R.string.restore_error_checksum,
            BackupRestoreFailure.MalformedManifest to R.string.restore_error_checksum,
            BackupRestoreFailure.InspectionExpired to R.string.restore_error_checksum,
            BackupRestoreFailure.RollbackFailed to R.string.restore_recovery_required_title,
            BackupRestoreFailure.RecoveryRequired to R.string.restore_recovery_required_title
        )

        mappings.forEach { (failure, expectedRes) ->
            assertThat(failure.toUserMessageRes()).isEqualTo(expectedRes)
        }
    }

    @Test
    fun `snapshot integrity failure maps to generic integrity message`() {
        val failures = BackupSnapshotIntegrityCode.entries.map { 
            BackupRestoreFailure.SnapshotIntegrityFailure(it)
        }
        
        failures.forEach { failure ->
            assertThat(failure.toUserMessageRes()).isEqualTo(R.string.restore_error_integrity_generic)
        }
    }
}
