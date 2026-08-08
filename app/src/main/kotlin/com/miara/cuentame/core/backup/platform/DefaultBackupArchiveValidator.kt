package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.*
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.preferences.model.ThemeMode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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
        val zis = ZipInputStream(NonClosingInputStream(inputStream))
        val entryMetadata = mutableMapOf<String, EntryInfo>()
        val jsonBytes = mutableMapOf<String, ByteArray>()

        var totalEntries = 0
        var currentTotalUncompressedSize = 0L

        try {
            zis.use { zip ->
                var entry: ZipEntry? = zip.nextEntry
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
                    val shouldBuffer = BackupFormatV1Contract.CORE_ENTRIES.contains(name)
                    val entryBuffer = if (shouldBuffer) ByteArrayOutputStream() else null

                    var n: Int
                    while (zip.read(buffer).also { n = it } != -1) {
                        digest.update(buffer, 0, n)
                        entrySize = BackupByteMath.addExact(entrySize, n.toLong())
                        currentTotalUncompressedSize = BackupByteMath.addExact(currentTotalUncompressedSize, n.toLong())
                        
                        if (currentTotalUncompressedSize > BackupLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                        }

                        entryBuffer?.write(buffer, 0, n)
                        if (shouldBuffer) {
                            val limit = when (name) {
                                BackupFormatV1Contract.MANIFEST_ENTRY -> BackupLimits.MAX_MANIFEST_JSON_BYTES.toLong()
                                BackupFormatV1Contract.PREFERENCES_ENTRY -> BackupLimits.MAX_SETTINGS_JSON_BYTES.toLong()
                                BackupFormatV1Contract.DATABASE_ENTRY -> BackupLimits.MAX_DATABASE_JSON_BYTES.toLong()
                                BackupFormatV1Contract.CHECKSUMS_ENTRY -> BackupLimits.MAX_CHECKSUMS_JSON_BYTES.toLong()
                                else -> 0L
                            }
                            if (entrySize > limit) {
                                return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
                            }
                        }
                    }

                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    entryMetadata[name] = EntryInfo(name, checksum, entrySize)
                    if (entryBuffer != null) jsonBytes[name] = entryBuffer.toByteArray()

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: BackupSizeOverflowException) {
            return BackupValidationResult.Invalid(BackupValidationCode.LIMIT_EXCEEDED)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR)
        }

        // 1. Required entries existence
        for (required in BackupFormatV1Contract.CORE_ENTRIES) {
            if (!entryMetadata.containsKey(required)) {
                return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
            }
        }

        // 2. Decode manifest with strict UTF-8
        val manifestBytes = jsonBytes[BackupFormatV1Contract.MANIFEST_ENTRY]!!
        val manifestJson = decodeStrictUtf8(manifestBytes) ?: 
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        
        val manifest = try {
            jsonCodecs.reader.decodeFromString<BackupManifest>(manifestJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID)
        }

        // 3. Exact entry set check
        val manifestAttachmentPaths = manifest.attachments.map { it.archivePath }.toSet()
        val expectedEntries = BackupFormatV1Contract.CORE_ENTRIES + manifestAttachmentPaths
        val actualEntries = entryMetadata.keys

        if (actualEntries != expectedEntries) {
            val unexpected = actualEntries - expectedEntries
            if (unexpected.isNotEmpty()) return BackupValidationResult.Invalid(BackupValidationCode.UNEXPECTED_ENTRY)
            return BackupValidationResult.Invalid(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        }

        // 4. Checksums validation
        val checksumsBytes = jsonBytes[BackupFormatV1Contract.CHECKSUMS_ENTRY]!!
        val checksumsJson = decodeStrictUtf8(checksumsBytes) ?:
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
            
        val reportedChecksumsResult = ChecksumParser.parse(checksumsJson)
        if (reportedChecksumsResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
        }
        val reportedChecksums = reportedChecksumsResult.getOrThrow()

        if (reportedChecksums.keys != actualEntries - BackupFormatV1Contract.CHECKSUMS_ENTRY) {
            return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
        }

        for ((name, reportedHash) in reportedChecksums) {
            if (entryMetadata[name]?.checksum != reportedHash) {
                return BackupValidationResult.Invalid(BackupValidationCode.CHECKSUM_MISMATCH)
            }
        }

        // 5. Basic manifest rules
        val manifestResult = BackupManifestValidator.validate(manifest)
        if (manifestResult is BackupValidationResult.Invalid) return manifestResult

        // 6. Preferences
        val settingsBytes = jsonBytes[BackupFormatV1Contract.PREFERENCES_ENTRY]!!
        val settingsJson = decodeStrictUtf8(settingsBytes) ?:
            return BackupValidationResult.Invalid(BackupValidationCode.PREFERENCES_INVALID)
            
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

        // 7. Database
        val dbBytes = jsonBytes[BackupFormatV1Contract.DATABASE_ENTRY]!!
        val dbJson = decodeStrictUtf8(dbBytes) ?:
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID)
            
        val dbDto = try {
            jsonCodecs.reader.decodeFromString<BackupSnapshotDto>(dbJson)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID)
        }

        if (manifest.tableMetadata != createTableMetadata(dbDto, manifest.databaseSchemaVersion)) {
            return BackupValidationResult.Invalid(BackupValidationCode.MANIFEST_INVALID, BackupValidationDiagnostic.TABLE_METADATA_MISMATCH)
        }

        val integrityResult = BackupSnapshotIntegrityValidator.validate(dbDto, manifest)
        if (integrityResult.isFailure) {
            return BackupValidationResult.Invalid(BackupValidationCode.SNAPSHOT_INVALID, BackupValidationDiagnostic.SNAPSHOT_INTEGRITY_FAILURE)
        }

        // 8. Attachments
        if (manifest.backupFormatVersion == BackupFormatV1Contract.BACKUP_FORMAT_VERSION) {
            if (manifest.attachments.isNotEmpty() ||
                dbDto.purchaseReceipts.any { it.attachmentId != null } ||
                dbDto.wasteEvents.any { it.attachmentId != null }
            ) {
                return BackupValidationResult.Invalid(BackupValidationCode.ATTACHMENTS_NOT_SUPPORTED)
            }
        }

        return BackupValidationResult.Valid(manifest)
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? {
        return try {
            val decoder = Charset.forName("UTF-8").newDecoder()
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun createTableMetadata(dbDto: BackupSnapshotDto, schemaVersion: Int): Map<String, TableMetadata> {
        val tables = mutableMapOf(
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
        )
        if (schemaVersion >= 3) {
            tables["preparation_recipes"] = TableMetadata(dbDto.preparationRecipes.size, false)
            tables["preparation_recipe_components"] = TableMetadata(dbDto.preparationRecipeComponents.size, false)
        }
        if (schemaVersion >= 4) {
            tables["production_batches"] = TableMetadata(dbDto.productionBatches.size, false)
            tables["production_batch_components"] = TableMetadata(dbDto.productionBatchComponents.size, false)
        }
        if (schemaVersion >= 6) {
            tables["purchase_invoice_ocr_results"] = TableMetadata(dbDto.purchaseInvoiceOcrResults.size, false)
            tables["purchase_invoice_ocr_pages"] = TableMetadata(dbDto.purchaseInvoiceOcrPages.size, false)
        }
        if (schemaVersion >= 7) {
            tables["purchase_invoice_parse_results"] = TableMetadata(dbDto.purchaseInvoiceParseResults.size, false)
            tables["purchase_invoice_parsed_lines"] = TableMetadata(dbDto.purchaseInvoiceParsedLines.size, false)
        }
        return tables.entries.sortedBy { it.key }.associate { it.key to it.value }
    }

    private data class EntryInfo(val name: String, val checksum: String, val size: Long)
}
