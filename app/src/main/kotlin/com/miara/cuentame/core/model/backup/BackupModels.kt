package com.miara.cuentame.core.model.backup

import com.miara.cuentame.core.database.entity.*
import kotlinx.serialization.Serializable
import java.time.Instant

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
    val attachments: List<AttachmentMetadata>,
    val preferences: Map<String, String>,
    val checksumAlgorithm: String = "SHA-256"
)

@Serializable
data class TableMetadata(
    val entryCount: Int,
    val isDerived: Boolean
)

@Serializable
data class AttachmentMetadata(
    val archivePath: String,
    val originalUri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val checksum: String
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

sealed interface BackupResult {
    data class Success(val manifest: BackupManifest) : BackupResult
    sealed interface Error : BackupResult {
        data object DestinationUnavailable : Error
        data object PermissionDenied : Error
        data object InsufficientStorage : Error
        data class SerializationFailure(val cause: Throwable) : Error
        data class DatabaseReadFailure(val cause: Throwable) : Error
        data class MissingAttachment(val uri: String) : Error
        data class UnreadableAttachment(val uri: String, val cause: Throwable) : Error
        data class ChecksumFailure(val entryName: String) : Error
        data class ArchiveValidationFailure(val reason: String) : Error
        data object UnsupportedPersistentData : Error
        data object OperationCancelled : Error
        data class Unknown(val cause: Throwable) : Error
    }
}

sealed interface BackupValidationResult {
    data class Valid(val manifest: BackupManifest) : BackupValidationResult
    data class Invalid(val reason: String) : BackupValidationResult
}
