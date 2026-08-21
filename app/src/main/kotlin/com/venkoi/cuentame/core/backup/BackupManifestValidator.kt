package com.venkoi.cuentame.core.backup

import com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract
import com.venkoi.cuentame.core.model.backup.BackupManifest
import com.venkoi.cuentame.core.model.backup.BackupValidationCode
import com.venkoi.cuentame.core.model.backup.BackupValidationDiagnostic
import com.venkoi.cuentame.core.model.backup.BackupValidationResult
import com.venkoi.cuentame.core.model.locale.SupportedAppLocale
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Currency

object BackupManifestValidator {

    fun validate(manifest: BackupManifest): BackupValidationResult {
        if (manifest.backupFormatVersion != BackupFormatV1Contract.BACKUP_FORMAT_VERSION) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.VERSION_MISMATCH)
        }

        if (manifest.databaseSchemaVersion !in BackupFormatV1Contract.SUPPORTED_RESTORE_DATABASE_SCHEMA_VERSIONS) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.DATABASE_SCHEMA_MISMATCH)
        }

        try {
            val instant = Instant.parse(manifest.createdAtUtc)
            if (instant.toString() != manifest.createdAtUtc) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TIMESTAMP_INVALID)
            }
        } catch (e: DateTimeParseException) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TIMESTAMP_INVALID)
        }

        if (!BackupApplicationIdentity.isAccepted(manifest.applicationId)) {
            return BackupValidationResult.Invalid(
                BackupValidationCode.MANIFEST_INVALID,
                BackupValidationDiagnostic.APPLICATION_ID_MISMATCH
            )
        }
        if (manifest.appVersionName.isBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.appVersionCode < 0) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        
        if (manifest.restaurantId.isNullOrBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        if (manifest.restaurantName.isNullOrBlank()) return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)

        val tag = manifest.localeTag
        if (tag.isNullOrBlank() || SupportedAppLocale.fromLanguageTag(tag) == null) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.LOCALE_UNSUPPORTED)
        }
        try {
            manifest.currencyCode?.let { Currency.getInstance(it) } ?: return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.CURRENCY_INVALID)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.CURRENCY_INVALID)
        }

        if (manifest.checksumAlgorithm != BackupFormatV1Contract.CHECKSUM_ALGORITHM) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        val sections = manifest.includedSections.toSet()
        if (sections != BackupFormatV1Contract.REQUIRED_SECTIONS || manifest.includedSections.size != sections.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        val tables = manifest.tableMetadata.keys
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(manifest.databaseSchemaVersion)
        if (tables != expectedTables) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
        }

        for ((tableName, metadata) in manifest.tableMetadata) {
            if (metadata.entryCount < 0) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
            }
            val expectedDerived = BackupFormatV1Contract.DERIVED_TABLES.contains(tableName)
            if (metadata.isDerived != expectedDerived) {
                return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
            }
        }

        if (manifest.attachments.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
            return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED, BackupValidationDiagnostic.ATTACHMENT_COUNT_EXCEEDED)
        }

        val attachmentIds = manifest.attachments.map { it.attachmentId }
        if (attachmentIds.any { it.isBlank() } || attachmentIds.distinct().size != attachmentIds.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        val archivePaths = manifest.attachments.map { it.archivePath }
        if (archivePaths.any { it.isBlank() } || archivePaths.distinct().size != archivePaths.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        for (att in manifest.attachments) {
            if (att.displayName.isBlank() || att.sizeBytes < 0 || !BackupFormatV1Contract.isValidChecksum(att.checksumSha256) || att.referencedBy.isEmpty() || att.referencedBy.distinct().size != att.referencedBy.size) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            for (ref in att.referencedBy) {
                if (ref.recordId.isBlank() || ref.recordType !in BackupFormatV1Contract.SUPPORTED_ATTACHMENT_RECORD_TYPES) {
                    return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
                }
            }
        }

        return BackupValidationResult.Valid(manifest)
    }
}
