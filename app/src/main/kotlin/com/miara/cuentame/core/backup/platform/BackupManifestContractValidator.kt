package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.AttachmentFilenameSanitizer
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupRestoreFailure

/**
 * Pure validator for cross-checking BackupManifest metadata against the actual
 * BackupSnapshotDto and physical ZIP entry results.
 */
object BackupManifestContractValidator {

    fun validateManifestStructure(
        manifest: BackupManifest,
        calculatedChecksums: Map<String, String>,
        calculatedSizes: Map<String, Long>
    ): BackupRestoreFailure? {
        // 1. Version and Format check
        if (manifest.backupFormatVersion != BackupFormatV1Contract.BACKUP_FORMAT_VERSION) {
            return BackupRestoreFailure.UnsupportedFormatVersion
        }
        if (manifest.databaseSchemaVersion != BackupFormatV1Contract.DATABASE_SCHEMA_VERSION) {
            return BackupRestoreFailure.IncompatibleSchemaVersion
        }
        if (manifest.checksumAlgorithm != BackupFormatV1Contract.CHECKSUM_ALGORITHM) {
            return BackupRestoreFailure.MalformedManifest
        }

        // 2. Identity validation
        if (manifest.restaurantId.isNullOrBlank()) return BackupRestoreFailure.MalformedManifest
        if (manifest.restaurantName.isNullOrBlank()) return BackupRestoreFailure.MalformedManifest
        if (manifest.localeTag.isNullOrBlank()) return BackupRestoreFailure.MalformedManifest
        if (manifest.currencyCode.isNullOrBlank()) return BackupRestoreFailure.MalformedManifest

        // 3. Section check
        if (!manifest.includedSections.containsAll(BackupFormatV1Contract.REQUIRED_SECTIONS)) {
            return BackupRestoreFailure.MalformedManifest
        }
        if (manifest.includedSections.size != manifest.includedSections.distinct().size) {
            return BackupRestoreFailure.MalformedManifest
        }

        // 4. Table metadata existence and validity
        if (!manifest.tableMetadata.keys.containsAll(BackupFormatV1Contract.EXPECTED_TABLES)) {
            return BackupRestoreFailure.MalformedManifest
        }
        val unexpectedTables = manifest.tableMetadata.keys - BackupFormatV1Contract.EXPECTED_TABLES
        if (unexpectedTables.isNotEmpty()) {
            return BackupRestoreFailure.MalformedManifest
        }
        if (manifest.tableMetadata.values.any { it.entryCount < 0 }) {
            return BackupRestoreFailure.MalformedManifest
        }

        // 5. Attachment cross-validation (Manifest side)
        val seenAttachmentIds = mutableSetOf<String>()
        val seenArchivePaths = mutableSetOf<String>()

        for (att in manifest.attachments) {
            if (!BackupFormatV1Contract.isValidAttachmentId(att.attachmentId)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (!seenAttachmentIds.add(att.attachmentId)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (att.archivePath != BackupFormatV1Contract.attachmentArchivePath(att.attachmentId, att.displayName)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (!seenArchivePaths.add(att.archivePath)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (!AttachmentFilenameSanitizer.isValid(att.displayName)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (att.sizeBytes < 0) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (!BackupFormatV1Contract.isValidChecksum(att.checksumSha256)) {
                return BackupRestoreFailure.MalformedManifest
            }

            // Cross-check with physical ZIP results
            val zipSize = calculatedSizes[att.archivePath]
            val zipChecksum = calculatedChecksums[att.archivePath]
            
            if (zipSize == null || zipChecksum == null) {
                return BackupRestoreFailure.ManifestMismatch
            }
            if (zipSize != att.sizeBytes) {
                return BackupRestoreFailure.AttachmentMismatch
            }
            if (!zipChecksum.equals(att.checksumSha256, ignoreCase = true)) {
                return BackupRestoreFailure.AttachmentMismatch
            }

            // References check
            if (att.referencedBy.isEmpty()) {
                return BackupRestoreFailure.MalformedManifest
            }
            val refKeys = att.referencedBy.map { "${it.recordType}:${it.recordId}" }
            if (refKeys.size != refKeys.distinct().size) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (att.referencedBy.any { it.recordId.isBlank() }) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (att.referencedBy.any { it.recordType !in BackupFormatV1Contract.SUPPORTED_ATTACHMENT_RECORD_TYPES }) {
                return BackupRestoreFailure.MalformedManifest
            }
        }

        // 6. Bijection: ZIP attachments vs Manifest attachments
        val zipPayloadPaths = calculatedChecksums.keys - BackupFormatV1Contract.CORE_ENTRIES
        if (zipPayloadPaths.any { !it.startsWith("attachments/") }) {
            return BackupRestoreFailure.UnexpectedEntry
        }
        if (zipPayloadPaths != seenArchivePaths) {
            return BackupRestoreFailure.ManifestMismatch
        }

        return null
    }

    fun validateSnapshotConsistency(
        manifest: BackupManifest,
        snapshot: BackupSnapshotDto
    ): BackupRestoreFailure? {
        // 1. Table counts check
        val actualCounts = mapOf(
            "restaurants" to snapshot.restaurants.size,
            "inventory_areas" to snapshot.inventoryAreas.size,
            "ingredient_categories" to snapshot.ingredientCategories.size,
            "units" to snapshot.units.size,
            "ingredients" to snapshot.ingredients.size,
            "ingredient_unit_options" to snapshot.ingredientUnitOptions.size,
            "suppliers" to snapshot.suppliers.size,
            "purchase_receipts" to snapshot.purchaseReceipts.size,
            "purchase_lines" to snapshot.purchaseLines.size,
            "stock_counts" to snapshot.stockCounts.size,
            "stock_count_areas" to snapshot.stockCountAreas.size,
            "stock_count_lines" to snapshot.stockCountLines.size,
            "waste_events" to snapshot.wasteEvents.size,
            "inventory_movements" to snapshot.inventoryMovements.size,
            "inventory_balance_projections" to snapshot.inventoryBalanceProjections.size,
            "ingredient_cost_projections" to snapshot.ingredientCostProjections.size
        )

        for ((table, metadata) in manifest.tableMetadata) {
            val actual = actualCounts[table]
            if (actual == null) {
                return BackupRestoreFailure.ManifestMismatch
            }
            if (actual != metadata.entryCount) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        // 2. Bi-directional attachment relationship validation
        
        // a. References from Snapshot -> Manifest
        val snapshotRefs = mutableSetOf<String>() // format: "attId:type:recordId"
        
        for (receipt in snapshot.purchaseReceipts) {
            receipt.attachmentId?.let { attId ->
                snapshotRefs.add("$attId:PURCHASE_RECEIPT:${receipt.id}")
            }
        }
        for (waste in snapshot.wasteEvents) {
            waste.attachmentId?.let { attId ->
                snapshotRefs.add("$attId:WASTE_EVENT:${waste.id}")
            }
        }

        // b. References from Manifest -> Snapshot
        val manifestRefs = mutableSetOf<String>()
        val manifestAttachmentIds = manifest.attachments.map { it.attachmentId }.toSet()

        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                manifestRefs.add("${att.attachmentId}:${ref.recordType}:${ref.recordId}")
            }
        }

        if (snapshotRefs != manifestRefs) {
            return BackupRestoreFailure.ManifestMismatch
        }

        // Verify all manifest attachment IDs exist in ZIP (implicitly handled by bijection in structure check, 
        // but here we check against snapshot IDs)
        for (ref in snapshotRefs) {
            val attId = ref.split(":")[0]
            if (attId !in manifestAttachmentIds) {
                return BackupRestoreFailure.SnapshotIntegrityFailure(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
            }
        }

        // c. Referenced record existence (redundant but safe to keep explicit)
        val purchaseIds = snapshot.purchaseReceipts.map { it.id }.toSet()
        val wasteIds = snapshot.wasteEvents.map { it.id }.toSet()

        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                when (ref.recordType) {
                    "PURCHASE_RECEIPT" -> if (ref.recordId !in purchaseIds) {
                        return BackupRestoreFailure.SnapshotIntegrityFailure(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
                    }
                    "WASTE_EVENT" -> if (ref.recordId !in wasteIds) {
                        return BackupRestoreFailure.SnapshotIntegrityFailure(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
                    }
                }
            }
        }

        return null
    }
}
