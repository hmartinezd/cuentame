package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.*
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.preferences.model.ThemeMode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBackupArchiveValidator @Inject constructor(
    private val jsonCodecs: BackupJsonCodecs
) : BackupArchiveValidator {

    override fun validate(inputStream: InputStream): BackupValidationResult {
        val zis = ZipInputStream(inputStream)
        val entryMetadata = mutableMapOf<String, EntryInfo>()
        val jsonPayloads = mutableMapOf<String, String>()

        var totalEntries = 0
        var currentTotalUncompressedSize = 0L

        try {
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                totalEntries++
                if (totalEntries > BackupLimits.MAX_ARCHIVE_ENTRY_COUNT) {
                    return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                }
                if (name.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES) {
                    return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH)
                }

                if (entryMetadata.containsKey(name)) {
                    return BackupValidationResult.Invalid(BackupValidationCode.DUPLICATE_ENTRY)
                }
                if (!ArchiveEntryValidator.isSafe(name)) {
                    return BackupValidationResult.Invalid(BackupValidationCode.UNSAFE_ENTRY_PATH)
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var entrySize = 0L
                val buffer = ByteArray(8192)
                val isJson = name.endsWith(".json")
                val jsonBuffer = if (isJson) ByteArrayOutputStream() else null

                var n: Int
                while (zis.read(buffer).also { n = it } != -1) {
                    digest.update(buffer, 0, n)
                    entrySize += n
                    currentTotalUncompressedSize += n
                    if (currentTotalUncompressedSize > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                    }

                    jsonBuffer?.write(buffer, 0, n)
                    if (isJson) {
                        val limit = when (name) {
                            "manifest.json" -> BackupLimits.MAX_MANIFEST_JSON_BYTES
                            "preferences/settings.json" -> BackupLimits.MAX_SETTINGS_JSON_BYTES
                            "data/database.json" -> BackupLimits.MAX_DATABASE_JSON_BYTES
                            "checksums.json" -> BackupLimits.MAX_CHECKSUMS_JSON_BYTES
                            else -> 10 * 1024 * 1024 // 10MB fallback
                        }
                        if (entrySize > limit) {
                            return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                        }
                    }
                }

                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                entryMetadata[name] = EntryInfo(name, checksum, entrySize)
                if (jsonBuffer != null) jsonPayloads[name] = jsonBuffer.toString("UTF-8")

                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR)
        }

        // 1. REQUIRED JSON entries existence
        val manifestJson = jsonPayloads["manifest.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val checksumsJson = jsonPayloads["checksums.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val settingsJson = jsonPayloads["preferences/settings.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        val dbJson = jsonPayloads["data/database.json"] ?: return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)

        // 2. manifest.json decoding
        val manifest = try {
            jsonCodecs.reader.decodeFromString<BackupManifest>(manifestJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        // 3. Exact archive entry set
        val expectedBaseEntries = setOf("manifest.json", "checksums.json", "preferences/settings.json", "data/database.json")
        val manifestAttachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val expectedEntries = expectedBaseEntries + manifestAttachmentPaths
        val actualEntries = entryMetadata.keys

        val unexpected = actualEntries - expectedEntries
        if (unexpected.isNotEmpty()) return BackupValidationResult.Invalid(BackupValidationCode.UNEXPECTED_ENTRY)

        val missing = expectedEntries - actualEntries
        if (missing.isNotEmpty()) return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)

        // 4. Strict Checksums Validation
        val reportedChecksumsResult = ChecksumParser.parse(checksumsJson)
        if (reportedChecksumsResult.isFailure) {
             val exception = reportedChecksumsResult.exceptionOrNull()
             return if (exception is ChecksumKeySetMismatchException) {
                 BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
             } else {
                 BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
             }
        }
        val reportedChecksums = reportedChecksumsResult.getOrThrow()

        if (reportedChecksums.keys != actualEntries - "checksums.json") {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
        }

        for ((name, reportedHash) in reportedChecksums) {
            val actualHash = entryMetadata[name]?.checksum
            if (actualHash != reportedHash) {
                return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH)
            }
        }

        // 5. Basic manifest validation
        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult is BackupValidationResult.Invalid) return manifestResult

        // 6. Preferences Integrity
        val prefsDto = try {
            jsonCodecs.reader.decodeFromString<BackupPreferencesDto>(settingsJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        }
        if (SupportedAppLocale.fromLanguageTag(prefsDto.appLocaleTag) == null) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        }
        if (prefsDto.appLocaleTag != manifest.localeTag) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        }
        try {
            ThemeMode.valueOf(prefsDto.themeMode)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
        }

        // 7. Database and Table Metadata
        val dbDto = try {
            jsonCodecs.reader.decodeFromString<BackupSnapshotDto>(dbJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID)
        }

        val actualTableMetadata = createTableMetadata(dbDto)
        if (manifest.tableMetadata != actualTableMetadata) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
        }

        val integrityResult = BackupSnapshotIntegrityValidator.validate(dbDto, manifest)
        if (integrityResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, BackupValidationDiagnostic.SNAPSHOT_INTEGRITY_FAILURE)
        }

        // 8. Complete attachment cross-validation
        val manifestAttachments = manifest.attachments
        
        // V1 ID contract check: exactly 16 lowercase hex chars
        val v1IdRegex = Regex("^[0-9a-f]{16}$")

        if (manifestAttachments.map { it.attachmentId }.any { !v1IdRegex.matches(it) }) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        if (manifestAttachments.map { it.attachmentId }.distinct().size != manifestAttachments.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }
        if (manifestAttachments.map { it.archivePath }.distinct().size != manifestAttachments.size) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        val dbAttachmentRefs = mutableSetOf<AttachmentReferenceKey>()

        dbDto.purchaseReceipts.forEach { r ->
            r.attachmentId?.let { id ->
                dbAttachmentRefs.add(AttachmentReferenceKey(id, "PURCHASE_RECEIPT", r.id))
            }
        }
        dbDto.wasteEvents.forEach { w ->
            w.attachmentId?.let { id ->
                dbAttachmentRefs.add(AttachmentReferenceKey(id, "WASTE_EVENT", w.id))
            }
        }

        val dbAttachmentIds = dbAttachmentRefs.map { it.attachmentId }.toSet()
        val manifestAttachmentIds = manifestAttachments.map { it.attachmentId }.toSet()
        if (dbAttachmentIds != manifestAttachmentIds) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
        }

        val manifestAttachmentRefs = manifestAttachments.flatMap { att ->
            att.referencedBy.map { ref -> AttachmentReferenceKey(att.attachmentId, ref.recordType, ref.recordId) }
        }.toSet()

        if (dbAttachmentRefs != manifestAttachmentRefs) {
            return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_REFERENCE_MISMATCH)
        }

        for (att in manifestAttachments) {
            if (!AttachmentFilenameSanitizer.isValid(att.displayName)) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            }
            if (att.archivePath != "attachments/${att.attachmentId}/${att.displayName}") {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_PATH_MISMATCH)
            }
            val actual = entryMetadata[att.archivePath] ?: return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID)
            if (actual.size != att.sizeBytes) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_SIZE_MISMATCH)
            }
            if (actual.checksum != att.checksumSha256) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENT_INVALID, BackupValidationDiagnostic.ATTACHMENT_CHECKSUM_MISMATCH)
            }
        }

        return BackupValidationResult.Valid(manifest)
    }

    private fun createTableMetadata(dbDto: BackupSnapshotDto): Map<String, TableMetadata> = mapOf(
        "restaurants" to TableMetadata(dbDto.restaurants.size, false),
        "inventory_areas" to TableMetadata(dbDto.inventoryAreas.size, false),
        "ingredient_categories" to TableMetadata(dbDto.ingredientCategories.size, false),
        "units" to TableMetadata(dbDto.units.size, false),
        "ingredients" to TableMetadata(dbDto.ingredients.size, false),
        "ingredient_unit_options" to TableMetadata(dbDto.ingredientUnitOptions.size, false),
        "suppliers" to TableMetadata(dbDto.suppliers.size, false),
        "purchase_receipts" to TableMetadata(dbDto.purchaseReceipts.size, false),
        "purchase_lines" to TableMetadata(dbDto.purchaseLines.size, false),
        "stock_counts" to TableMetadata(dbDto.stockCounts.size, false),
        "stock_count_areas" to TableMetadata(dbDto.stockCountAreas.size, false),
        "stock_count_lines" to TableMetadata(dbDto.stockCountLines.size, false),
        "waste_events" to TableMetadata(dbDto.wasteEvents.size, false),
        "inventory_movements" to TableMetadata(dbDto.inventoryMovements.size, false),
        "inventory_balance_projections" to TableMetadata(dbDto.inventoryBalanceProjections.size, true),
        "ingredient_cost_projections" to TableMetadata(dbDto.ingredientCostProjections.size, true)
    ).entries.sortedBy { it.key }.associate { it.key to it.value }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
}
