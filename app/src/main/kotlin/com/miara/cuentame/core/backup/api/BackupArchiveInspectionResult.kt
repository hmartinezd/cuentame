package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupRestorePreview

/**
 * Result of inspecting a backup archive.
 */
sealed interface BackupArchiveInspectionResult {
    data class Ready(
        val archive: InspectedBackupArchive,
        val preview: BackupRestorePreview,
        val eligibility: BackupRestoreEligibility
    ) : BackupArchiveInspectionResult

    data class Failure(
        val reason: BackupRestoreFailure
    ) : BackupArchiveInspectionResult
}
