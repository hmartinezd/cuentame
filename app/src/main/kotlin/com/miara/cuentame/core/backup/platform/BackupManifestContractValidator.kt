package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.AttachmentFilenameSanitizer
import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.api.AttachmentReferenceKey
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import android.util.Log

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
        if (manifest.databaseSchemaVersion !in BackupFormatV1Contract.SUPPORTED_RESTORE_DATABASE_SCHEMA_VERSIONS) {
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
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(manifest.databaseSchemaVersion)
        if (!manifest.tableMetadata.keys.containsAll(expectedTables)) {
            return BackupRestoreFailure.MalformedManifest
        }
        val unexpectedTables = manifest.tableMetadata.keys - expectedTables
        if (unexpectedTables.isNotEmpty()) {
            return BackupRestoreFailure.MalformedManifest
        }
        
        for ((tableName, metadata) in manifest.tableMetadata) {
            if (metadata.entryCount < 0) {
                return BackupRestoreFailure.MalformedManifest
            }
            val expectedDerived = tableName in BackupFormatV1Contract.DERIVED_TABLES
            if (metadata.isDerived != expectedDerived) {
                return BackupRestoreFailure.MalformedManifest
            }
        }

        // 5. Attachment cross-validation (Manifest side)
        val seenAttachmentIds = mutableSetOf<String>()
        val seenArchivePaths = mutableSetOf<String>()
        val seenRecordReferences = mutableSetOf<String>()

        for (att in manifest.attachments) {
            try {
                PurchaseAttachmentLocation.validateSegment(att.attachmentId, "attachmentId")
            } catch (e: Exception) {
                return BackupRestoreFailure.MalformedManifest
            }
            
            if (!BackupFormatV1Contract.isValidAttachmentId(att.attachmentId)) {
                return BackupRestoreFailure.MalformedManifest
            }
            if (!seenAttachmentIds.add(att.attachmentId)) {
                return BackupRestoreFailure.MalformedManifest
            }
            
            try {
                PurchaseAttachmentLocation.validateSegment(att.displayName, "displayName")
            } catch (e: Exception) {
                return BackupRestoreFailure.MalformedManifest
            }

            if (att.archivePath != BackupFormatV1Contract.attachmentArchivePath(att.attachmentId, att.displayName)) {
                return BackupRestoreFailure.MalformedManifest
            }
            // seenArchivePaths check is redundant but retained for clarity in external contract
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
            
            for (ref in att.referencedBy) {
                if (ref.recordId.isBlank()) return BackupRestoreFailure.MalformedManifest
                try {
                    PurchaseAttachmentLocation.validateSegment(ref.recordId, "recordId")
                } catch (e: Exception) {
                    return BackupRestoreFailure.MalformedManifest
                }
                
                if (ref.recordType !in BackupFormatV1Contract.SUPPORTED_ATTACHMENT_RECORD_TYPES) {
                    return BackupRestoreFailure.MalformedManifest
                }
                
                val refKey = "${ref.recordType}:${ref.recordId}"
                if (!seenRecordReferences.add(refKey)) {
                    // One record cannot have multiple attachments in this schema
                    return BackupRestoreFailure.MalformedManifest
                }
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
        // 1. Validate table counts
        val actualCounts = mutableMapOf(
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
        if (manifest.databaseSchemaVersion >= 11) {
            actualCounts["stock_count_item_order"] = snapshot.stockCountItemOrder.size
        } else if (snapshot.stockCountItemOrder.isNotEmpty()) {
            return BackupRestoreFailure.ManifestMismatch
        }

        if (manifest.databaseSchemaVersion >= 3) {
            actualCounts["preparation_recipes"] = snapshot.preparationRecipes.size
            actualCounts["preparation_recipe_components"] = snapshot.preparationRecipeComponents.size
        } else {
            if (snapshot.preparationRecipes.isNotEmpty() || snapshot.preparationRecipeComponents.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        if (manifest.databaseSchemaVersion >= 4) {
            actualCounts["production_batches"] = snapshot.productionBatches.size
            actualCounts["production_batch_components"] = snapshot.productionBatchComponents.size
        } else {
            if (snapshot.productionBatches.isNotEmpty() || snapshot.productionBatchComponents.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }
        
        if (manifest.databaseSchemaVersion >= 6) {
            actualCounts["purchase_invoice_ocr_results"] = snapshot.purchaseInvoiceOcrResults.size
            actualCounts["purchase_invoice_ocr_pages"] = snapshot.purchaseInvoiceOcrPages.size
        } else {
            if (snapshot.purchaseInvoiceOcrResults.isNotEmpty() || snapshot.purchaseInvoiceOcrPages.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        if (manifest.databaseSchemaVersion >= 7) {
            actualCounts["purchase_invoice_parse_results"] = snapshot.purchaseInvoiceParseResults.size
            actualCounts["purchase_invoice_parsed_lines"] = snapshot.purchaseInvoiceParsedLines.size
        } else {
            if (snapshot.purchaseInvoiceParseResults.isNotEmpty() || snapshot.purchaseInvoiceParsedLines.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        if (manifest.databaseSchemaVersion >= 8) {
            actualCounts["supplier_item_mappings"] = snapshot.supplierItemMappings.size
            actualCounts["purchase_invoice_line_matches"] = snapshot.purchaseInvoiceLineMatches.size
        } else {
            if (snapshot.supplierItemMappings.isNotEmpty() || snapshot.purchaseInvoiceLineMatches.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        if (manifest.databaseSchemaVersion >= 9) {
            actualCounts["purchase_invoice_draft_applications"] = snapshot.purchaseInvoiceDraftApplications.size
            actualCounts["purchase_invoice_line_origins"] = snapshot.purchaseInvoiceLineOrigins.size
        } else {
            if (snapshot.purchaseInvoiceDraftApplications.isNotEmpty() || snapshot.purchaseInvoiceLineOrigins.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        if (manifest.databaseSchemaVersion >= 12) {
            actualCounts["menu_recipes"] = snapshot.menuRecipes.size
            actualCounts["menu_recipe_components"] = snapshot.menuRecipeComponents.size
        } else {
            if (snapshot.menuRecipes.isNotEmpty() || snapshot.menuRecipeComponents.isNotEmpty()) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        for ((table, metadata) in manifest.tableMetadata) {
            val actual = actualCounts[table] ?: return BackupRestoreFailure.ManifestMismatch
            if (actual != metadata.entryCount) {
                return BackupRestoreFailure.ManifestMismatch
            }
        }

        // 2. Build and compare bi-directional attachment reference keys
        val snapshotRefs = mutableSetOf<AttachmentReferenceKey>()

        for (receipt in snapshot.purchaseReceipts) {
            receipt.attachmentId?.let { 
                snapshotRefs.add(AttachmentReferenceKey(it, "PURCHASE_RECEIPT", receipt.id)) 
            }
        }
        for (waste in snapshot.wasteEvents) {
            waste.attachmentId?.let { 
                snapshotRefs.add(AttachmentReferenceKey(it, "WASTE_EVENT", waste.id)) 
            }
        }

        val manifestRefs = mutableSetOf<AttachmentReferenceKey>()
        for (att in manifest.attachments) {
            for (ref in att.referencedBy) {
                manifestRefs.add(AttachmentReferenceKey(att.attachmentId, ref.recordType, ref.recordId))
            }
        }

        if (snapshotRefs != manifestRefs) {
            return BackupRestoreFailure.ManifestMismatch
        }

        return null
    }
}
