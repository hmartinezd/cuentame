package com.miara.cuentame.core.backup

import com.miara.cuentame.core.model.backup.BackupManifest
import java.time.format.DateTimeParseException
import java.time.Instant

object BackupManifestValidator {

    private val REQUIRED_SECTIONS = setOf("data", "preferences", "attachments")
    private val EXPECTED_TABLES = setOf(
        "restaurants", "inventory_areas", "ingredient_categories", "units",
        "ingredients", "ingredient_unit_options", "suppliers", "purchase_receipts",
        "purchase_lines", "stock_counts", "stock_count_areas", "stock_count_lines",
        "waste_events", "inventory_movements", "inventory_balance_projections",
        "ingredient_cost_projections"
    )

    fun validate(manifest: BackupManifest): Result<Unit> {
        if (manifest.backupFormatVersion != 1) {
            return Result.failure(Exception("Unsupported backup format version: ${manifest.backupFormatVersion}"))
        }

        try {
            Instant.parse(manifest.createdAtUtc)
        } catch (e: DateTimeParseException) {
            return Result.failure(Exception("Invalid createdAtUtc format: ${manifest.createdAtUtc}"))
        }

        if (manifest.applicationId.isBlank()) return Result.failure(Exception("applicationId is blank"))
        if (manifest.appVersionName.isBlank()) return Result.failure(Exception("appVersionName is blank"))
        if (manifest.appVersionCode < 0) return Result.failure(Exception("appVersionCode is negative"))
        if (manifest.databaseSchemaVersion <= 0) return Result.failure(Exception("databaseSchemaVersion must be positive"))
        if (manifest.restaurantId.isNullOrBlank()) return Result.failure(Exception("restaurantId is blank"))
        if (manifest.restaurantName.isNullOrBlank()) return Result.failure(Exception("restaurantName is blank"))
        
        if (manifest.checksumAlgorithm != "SHA-256") {
            return Result.failure(Exception("Unsupported checksum algorithm: ${manifest.checksumAlgorithm}"))
        }

        val sections = manifest.includedSections.toSet()
        if (sections != REQUIRED_SECTIONS) {
            return Result.failure(Exception("Mismatched includedSections. Expected $REQUIRED_SECTIONS, got $sections"))
        }
        if (manifest.includedSections.size != sections.size) {
            return Result.failure(Exception("Duplicate includedSections found"))
        }

        val tables = manifest.tableMetadata.keys
        if (tables != EXPECTED_TABLES) {
            val missing = EXPECTED_TABLES - tables
            val unknown = tables - EXPECTED_TABLES
            return Result.failure(Exception("Mismatched tableMetadata. Missing: $missing, Unknown: $unknown"))
        }

        for ((tableName, metadata) in manifest.tableMetadata) {
            if (metadata.entryCount < 0) {
                return Result.failure(Exception("Negative entryCount for table: $tableName"))
            }
            val expectedDerived = tableName == "inventory_balance_projections" || tableName == "ingredient_cost_projections"
            if (metadata.isDerived != expectedDerived) {
                return Result.failure(Exception("Incorrect isDerived flag for table: $tableName. Expected $expectedDerived"))
            }
        }

        return Result.success(Unit)
    }
}
