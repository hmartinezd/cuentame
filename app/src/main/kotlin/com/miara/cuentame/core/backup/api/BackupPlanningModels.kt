package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto

sealed interface BackupPlanningResult {
    data class Success(
        val plan: BackupPlan
    ) : BackupPlanningResult

    data class Failure(
        val reason: BackupPlanningFailure
    ) : BackupPlanningResult
}

sealed interface BackupPlanningFailure {
    data object LocaleReconciliationFailed : BackupPlanningFailure
    data object UnsupportedRestaurantLocale : BackupPlanningFailure
    data object UnsupportedPreferencesLocale : BackupPlanningFailure
    data object PreferencesLocaleMismatch : BackupPlanningFailure
    data object InvalidPreferences : BackupPlanningFailure
    data object InvalidSnapshot : BackupPlanningFailure
    data object MissingAttachmentSource : BackupPlanningFailure
    data object UnreadableAttachment : BackupPlanningFailure
    data object InvalidAttachmentMetadata : BackupPlanningFailure
    data object AttachmentLimitExceeded : BackupPlanningFailure
    data object JsonLimitExceeded : BackupPlanningFailure
}

data class PlannedBackupAttachment(
    val sourceUri: AttachmentSourceUri,
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val mimeType: String?,
    val references: List<BackupAttachmentReference>
)

data class BackupPlan(
    val snapshotDto: BackupSnapshotDto,
    val snapshotJson: ByteArray,
    val preferencesDto: BackupPreferencesDto,
    val preferencesJson: ByteArray,
    val baseManifest: BackupManifest,
    val attachments: List<PlannedBackupAttachment>
)
