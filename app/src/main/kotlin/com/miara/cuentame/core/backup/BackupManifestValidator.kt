package com.miara.cuentame.core.backup

import com.miara.cuentame.core.model.backup.BackupManifest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Currency

object BackupManifestValidator {

    private val REQUIRED_SECTIONS = setOf("data", "preferences", "attachments")
    private val EXPECTED_TABLES = setOf(
        "restaurants", "inventory_areas", "ingredient_categories", "units",
        "ingredients", "ingredient_unit_options", "suppliers", "purchase_receipts",
        "purchase_lines", "stock_counts", "stock_count_areas", "stock_count_lines",
        "waste_events", "inventory_movements", "inventory_balance_projections",
        "ingredient_cost_projections"
    )
    private val SUPPORTED_RECORD_TYPES = setOf("PURCHASE_RECEIPT", "WASTE_EVENT")
    private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")

    fun validate(manifest: BackupManifest): Result<Unit> {
        if (manifest.backupFormatVersion != 1) {
            return Result.failure(Exception("Unsupported backup format version: ${manifest.backupFormatVersion}"))
        }

        try {
            val instant = Instant.parse(manifest.createdAtUtc)
            if (instant.toString() != manifest.createdAtUtc) {
                return Result.failure(Exception("createdAtUtc is not in canonical UTC format"))
            }
        } catch (e: DateTimeParseException) {
            return Result.failure(Exception("Invalid createdAtUtc format: ${manifest.createdAtUtc}"))
        }

        if (manifest.applicationId.isBlank()) return Result.failure(Exception("applicationId is blank"))
        if (manifest.appVersionName.isBlank()) return Result.failure(Exception("appVersionName is blank"))
        if (manifest.appVersionCode < 0) return Result.failure(Exception("appVersionCode is negative"))
        if (manifest.databaseSchemaVersion <= 0) return Result.failure(Exception("databaseSchemaVersion must be positive"))
        if (manifest.restaurantId.isNullOrBlank()) return Result.failure(Exception("restaurantId is blank"))
        if (manifest.restaurantName.isNullOrBlank()) return Result.failure(Exception("restaurantName is blank"))

        // Strict Locale check — only supported app locales are accepted
        val tag = manifest.localeTag
        if (tag.isNullOrBlank()) return Result.failure(Exception("Missing localeTag"))
        if (tag !in SupportedAppLocales.ALL) {
            return Result.failure(Exception("Unsupported localeTag: $tag"))
        }
        try {
            manifest.currencyCode?.let { Currency.getInstance(it) } ?: throw Exception("Missing currencyCode")
        } catch (e: Exception) {
            return Result.failure(Exception("Invalid currencyCode: ${manifest.currencyCode}"))
        }

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

        // Attachment multiplicity & validation (before converting anything to a set)
        if (manifest.attachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
            return Result.failure(Exception("Exceeded maximum attachment limit"))
        }

        val attachmentIds = manifest.attachments.map { it.attachmentId }
        if (attachmentIds.any { it.isBlank() }) {
            return Result.failure(Exception("Blank attachment ID in manifest"))
        }
        if (attachmentIds.distinct().size != attachmentIds.size) {
            return Result.failure(Exception("Duplicate attachment ID in manifest"))
        }

        val archivePaths = manifest.attachments.map { it.archivePath }
        if (archivePaths.any { it.isBlank() }) {
            return Result.failure(Exception("Blank archive path in manifest"))
        }
        if (archivePaths.distinct().size != archivePaths.size) {
            return Result.failure(Exception("Duplicate archive path in manifest"))
        }

        for (att in manifest.attachments) {
            if (att.displayName.isBlank()) {
                return Result.failure(Exception("Blank display name in attachment metadata"))
            }
            if (att.sizeBytes < 0) {
                return Result.failure(Exception("Negative sizeBytes in attachment metadata"))
            }
            if (!SHA256_REGEX.matches(att.checksumSha256)) {
                return Result.failure(Exception("Invalid SHA-256 checksum in attachment metadata"))
            }
            if (att.referencedBy.isEmpty()) {
                return Result.failure(Exception("Attachment referencedBy list cannot be empty"))
            }
            if (att.referencedBy.distinct().size != att.referencedBy.size) {
                return Result.failure(Exception("Duplicate reference in attachment referencedBy list"))
            }
            for (ref in att.referencedBy) {
                if (ref.recordId.isBlank()) {
                    return Result.failure(Exception("Blank recordId in attachment reference"))
                }
                if (ref.recordType !in SUPPORTED_RECORD_TYPES) {
                    return Result.failure(Exception("Unsupported recordType in attachment reference"))
                }
            }
        }

        return Result.success(Unit)
    }
}
