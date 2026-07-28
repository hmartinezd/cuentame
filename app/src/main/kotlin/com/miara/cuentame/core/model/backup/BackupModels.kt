package com.miara.cuentame.core.model.backup

import com.miara.cuentame.core.database.entity.*
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

data class BackupSnapshot(
    val restaurants: List<RestaurantEntity>,
    val inventoryAreas: List<InventoryAreaEntity>,
    val ingredientCategories: List<IngredientCategoryEntity>,
    val units: List<UnitEntity>,
    val ingredients: List<IngredientEntity>,
    val ingredientUnitOptions: List<IngredientUnitOptionEntity>,
    val suppliers: List<SupplierEntity>,
    val purchaseReceipts: List<PurchaseReceiptEntity>,
    val purchaseLines: List<PurchaseLineEntity>,
    val stockCounts: List<StockCountEntity>,
    val stockCountAreas: List<StockCountAreaEntity>,
    val stockCountLines: List<StockCountLineEntity>,
    val wasteEvents: List<WasteEventEntity>,
    val inventoryMovements: List<InventoryMovementEntity>,
    val inventoryBalanceProjections: List<InventoryBalanceProjectionEntity>,
    val ingredientCostProjections: List<IngredientCostProjectionEntity>
)

@Serializable
data class BackupPreferencesDto(
    val themeMode: String,
    val dynamicColorEnabled: Boolean,
    val appLocaleTag: String
)

sealed interface BackupResult {
    data class Success(val manifest: BackupManifest) : BackupResult
    sealed interface Error : BackupResult {
        data object DestinationUnavailable : Error
        data object PermissionDenied : Error
        data object InsufficientStorage : Error
        data class LimitExceeded(val message: String) : Error
        data class SerializationFailure(val cause: Throwable) : Error
        data class DatabaseSnapshotFailure(val cause: Throwable) : Error
        data class PreferencesReadFailure(val cause: Throwable) : Error
        data class MissingAttachment(val attachmentId: String) : Error
        data class UnreadableAttachment(val attachmentId: String, val cause: Throwable) : Error
        data class ChecksumFailure(val entryName: String) : Error
        data class ArchiveValidationFailure(val code: BackupValidationCode, val reason: String) : Error
        data object UnsupportedPersistentData : Error
        data object OperationCancelled : Error
        data object UnexpectedInternalFailure : Error
        data class SystemIOFailure(val cause: Throwable) : Error
    }
}

enum class BackupValidationCode {
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
    LIMIT_EXCEEDED
}

sealed interface BackupValidationResult {
    data class Valid(val manifest: BackupManifest) : BackupValidationResult
    data class Invalid(val code: BackupValidationCode, val reason: String) : BackupValidationResult
}
