package com.miara.cuentame.core.backup

import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupValidationCode
import com.miara.cuentame.core.model.backup.BackupValidationDiagnostic
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.locale.SupportedAppLocale
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

    fun validate(manifest: BackupManifest): BackupValidationResult {
        if (manifest.backupFormatVersion != 1) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.VERSION_MISMATCH)
        }

        try {
            val instant = Instant.parse(manifest.createdAtUtc)
            if (instant.toString() != manifest.createdAtUtc) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TIMESTAMP_INVALID)
            }
        } catch (e: DateTimeParseException) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TIMESTAMP_INVALID)
        }

        if (manifest.applicationId.isBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.appVersionName.isBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.appVersionCode < 0) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.databaseSchemaVersion <= 0) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.restaurantId.isNullOrBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.restaurantName.isNullOrBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)

        // Strict Locale check — only supported app locales are accepted
        val tag = manifest.localeTag
        if (tag.isNullOrBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.LOCALE_UNSUPPORTED)
        if (tag !in SupportedAppLocale.languageTags) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.LOCALE_UNSUPPORTED)
        }
        try {
            manifest.currencyCode?.let { Currency.getInstance(it) } ?: return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.CURRENCY_INVALID)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.CURRENCY_INVALID)
        }

        if (manifest.checksumAlgorithm != "SHA-256") {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        val sections = manifest.includedSections.toSet()
        if (sections != REQUIRED_SECTIONS) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }
        if (manifest.includedSections.size != sections.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        val tables = manifest.tableMetadata.keys
        if (tables != EXPECTED_TABLES) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
        }

        for ((tableName, metadata) in manifest.tableMetadata) {
            if (metadata.entryCount < 0) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
            }
            val expectedDerived = tableName == "inventory_balance_projections" || tableName == "ingredient_cost_projections"
            if (metadata.isDerived != expectedDerived) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
            }
        }

        // Attachment multiplicity & validation (before converting anything to a set)
        if (manifest.attachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
            return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, BackupValidationDiagnostic.ATTACHMENT_COUNT_EXCEEDED)
        }

        val attachmentIds = manifest.attachments.map { it.attachmentId }
        if (attachmentIds.any { it.isBlank() }) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }
        if (attachmentIds.distinct().size != attachmentIds.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        val archivePaths = manifest.attachments.map { it.archivePath }
        if (archivePaths.any { it.isBlank() }) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }
        if (archivePaths.distinct().size != archivePaths.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        for (att in manifest.attachments) {
            if (att.displayName.isBlank()) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            if (att.sizeBytes < 0) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            if (!SHA256_REGEX.matches(att.checksumSha256)) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_CHECKSUM_MISMATCH)
            }
            if (att.referencedBy.isEmpty()) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            if (att.referencedBy.distinct().size != att.referencedBy.size) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            for (ref in att.referencedBy) {
                if (ref.recordId.isBlank()) {
                    return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
                }
                if (ref.recordType !in SUPPORTED_RECORD_TYPES) {
                    return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
                }
            }
        }

        return BackupValidationResult.Valid(manifest)
    }
}
