package com.venkoi.restaurantops.core.backup.api

import com.venkoi.restaurantops.core.model.backup.BackupRestoreEligibility
import com.venkoi.restaurantops.core.model.backup.BackupRestoreFailure
import com.venkoi.restaurantops.core.model.backup.BackupRestorePreview

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
