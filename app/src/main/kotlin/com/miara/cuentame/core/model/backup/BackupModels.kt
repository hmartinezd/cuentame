package com.miara.cuentame.core.model.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val backupFormatVersion: Int,
    val createdAtUtc: String,
    val applicationId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseSchemaVersion: Int,
    val restaurantId: String?,
    val restaurantName: String?,
    val localeTag: String?,
    val currencyCode: String?,
    val tableMetadata: Map<String, TableMetadata>,
    val attachments: List<BackupAttachmentMetadata>,
    val includedSections: List<String>,
    val checksumAlgorithm: String = "SHA-256"
)

@Serializable
data class TableMetadata(
    val entryCount: Int,
    val isDerived: Boolean
)

@Serializable
data class BackupAttachmentMetadata(
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val checksumSha256: String,
    val referencedBy: List<BackupAttachmentReference>
)

@Serializable
data class BackupAttachmentReference(
    val recordType: String,
    val recordId: String
)

@Serializable
data class BackupPreferencesDto(
    val themeMode: String,
    val dynamicColorEnabled: Boolean,
    val appLocaleTag: String,
    val menuManagementEnabled: Boolean = true
)

/**
 * Eligibility status for backup restoration.
 */
sealed interface BackupRestoreEligibility {
    data object Eligible : BackupRestoreEligibility
    data object AttachmentsNotSupported : BackupRestoreEligibility
}

/**
 * Internal rollback model to preserve raw database state.
 */
@Serializable
data class RestoreDatabaseRollbackSnapshot(
    val snapshot: com.miara.cuentame.core.backup.model.BackupSnapshotDto,
    val purchaseReceiptAttachmentPaths: Map<String, String?>,
    val purchaseReceiptAttachmentDisplayNames: Map<String, String?>,
    val wasteEventAttachmentPaths: Map<String, String?>,
    val wasteEventAttachmentDisplayNames: Map<String, String?>,
    val attachmentInventory: List<com.miara.cuentame.core.backup.internal.RollbackAttachmentMetadata> = emptyList()
)

sealed interface BackupResult {
    data class Success(val manifest: BackupManifest) : BackupResult
    sealed interface Error : BackupResult {
        data object DestinationUnavailable : Error
        data object PermissionDenied : Error
        data object InsufficientStorage : Error
        data object LimitExceeded : Error
        data object SerializationFailure : Error
        data object DatabaseSnapshotFailure : Error
        data object PreferencesReadFailure : Error
        data object MissingAttachment : Error
        data object UnreadableAttachment : Error
        data object ChecksumFailure : Error
        data object RestaurantUnavailable : Error
        data object FilenamePreparationFailure : Error
        data object UnsupportedPersistentData : Error
        data object OperationCancelled : Error
        data object UnexpectedInternalFailure : Error
        data object SystemIOFailure : Error
        data object LocaleConsistencyFailure : Error
        data object AttachmentPreflightFailure : Error
        data object AttachmentsNotSupported : Error
        data object OperationInterrupted : Error

        data class ArchiveValidationFailure(
            val code: BackupValidationCode,
            val diagnostic: BackupValidationDiagnostic? = null
        ) : Error
    }
}

enum class BackupValidationCode {
    SYSTEM_IO_ERROR,
    UNSAFE_ENTRY_PATH,
    DUPLICATE_ENTRY,
    MISSING_REQUIRED_ENTRY,
    UNEXPECTED_ENTRY,
    CHECKSUM_PARSE_FAILURE,
    CHECKSUM_KEY_SET_MISMATCH,
    CHECKSUM_MISMATCH,
    MANIFEST_INVALID,
    PREFERENCES_INVALID,
    SNAPSHOT_INVALID,
    ATTACHMENT_INVALID,
    ATTACHMENTS_NOT_SUPPORTED,
    LIMIT_EXCEEDED
}

enum class BackupValidationDiagnostic {
    VERSION_MISMATCH,
    TIMESTAMP_INVALID,
    LOCALE_UNSUPPORTED,
    CURRENCY_INVALID,
    TABLE_METADATA_MISMATCH,
    ATTACHMENT_COUNT_EXCEEDED,
    ATTACHMENT_CHECKSUM_MISMATCH,
    ATTACHMENT_SIZE_MISMATCH,
    ATTACHMENT_PATH_MISMATCH,
    ATTACHMENT_REFERENCE_MISMATCH,
    SNAPSHOT_INTEGRITY_FAILURE,
    CHECKSUM_MISSING,
    DATABASE_SCHEMA_MISMATCH
}

sealed interface BackupValidationResult {
    data class Valid(val manifest: BackupManifest) : BackupValidationResult
    data class Invalid(
        val code: BackupValidationCode,
        val diagnostic: BackupValidationDiagnostic? = null
    ) : BackupValidationResult
}
