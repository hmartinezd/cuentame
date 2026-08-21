package com.venkoi.restaurantops.core.backup.api

import com.venkoi.restaurantops.core.backup.model.BackupSnapshotDto
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.model.backup.BackupPreferencesDto
import java.util.Collections

/**
 * Validated in-memory representation of a backup archive suitable for application.
 * Performs defensive copies of all mutable-origin collections.
 */
data class InspectedBackupArchive private constructor(
    val snapshot: BackupSnapshotDto,
    val preferences: BackupPreferencesDto,
    val manifest: BackupManifest,
    val attachmentSummaries: List<InspectedBackupAttachment>,
    val source: BackupDocumentUri,
    val fingerprint: BackupArchiveFingerprint
) {
    companion object {
        fun create(
            snapshot: BackupSnapshotDto,
            preferences: BackupPreferencesDto,
            manifest: BackupManifest,
            attachmentSummaries: List<InspectedBackupAttachment>,
            source: BackupDocumentUri,
            fingerprint: BackupArchiveFingerprint
        ): InspectedBackupArchive {
            return InspectedBackupArchive(
                snapshot = snapshot.copy(
                    restaurants = Collections.unmodifiableList(snapshot.restaurants.toList()),
                    inventoryAreas = Collections.unmodifiableList(snapshot.inventoryAreas.toList()),
                    ingredientCategories = Collections.unmodifiableList(snapshot.ingredientCategories.toList()),
                    units = Collections.unmodifiableList(snapshot.units.toList()),
                    ingredients = Collections.unmodifiableList(snapshot.ingredients.toList()),
                    ingredientUnitOptions = Collections.unmodifiableList(snapshot.ingredientUnitOptions.toList()),
                    suppliers = Collections.unmodifiableList(snapshot.suppliers.toList()),
                    purchaseReceipts = Collections.unmodifiableList(snapshot.purchaseReceipts.toList()),
                    purchaseLines = Collections.unmodifiableList(snapshot.purchaseLines.toList()),
                    stockCounts = Collections.unmodifiableList(snapshot.stockCounts.toList()),
                    stockCountAreas = Collections.unmodifiableList(snapshot.stockCountAreas.toList()),
                    stockCountLines = Collections.unmodifiableList(snapshot.stockCountLines.toList()),
                    stockCountItemOrder = Collections.unmodifiableList(snapshot.stockCountItemOrder.toList()),
                    wasteEvents = Collections.unmodifiableList(snapshot.wasteEvents.toList()),
                    inventoryMovements = Collections.unmodifiableList(snapshot.inventoryMovements.toList()),
                    inventoryBalanceProjections = Collections.unmodifiableList(snapshot.inventoryBalanceProjections.toList()),
                    ingredientCostProjections = Collections.unmodifiableList(snapshot.ingredientCostProjections.toList())
                ),
                preferences = preferences.copy(),
                manifest = manifest.copy(
                    includedSections = Collections.unmodifiableList(manifest.includedSections.toList()),
                    tableMetadata = Collections.unmodifiableMap(manifest.tableMetadata.toMap()),
                    attachments = Collections.unmodifiableList(manifest.attachments.map { m ->
                        m.copy(referencedBy = Collections.unmodifiableList(m.referencedBy.toList()))
                    })
                ),
                attachmentSummaries = Collections.unmodifiableList(attachmentSummaries.toList()),
                source = source,
                fingerprint = fingerprint
            )
        }
    }
}

data class InspectedBackupAttachment(
    val attachmentId: String,
    val archivePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val checksumSha256: String
)
