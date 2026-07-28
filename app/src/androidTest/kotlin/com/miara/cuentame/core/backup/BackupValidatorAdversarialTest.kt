package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupValidationCode
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.backup.TableMetadata
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupValidatorAdversarialTest {

    private lateinit var context: Context
    private val checksumProvider = Sha256ChecksumProvider()
    private lateinit var repository: AndroidBackupRepository

    private val jsonWriter = Json { prettyPrint = true; encodeDefaults = true }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = AndroidBackupRepository(
            context,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            checksumProvider
        )
    }

    private val validTableMetadata = mapOf(
        "restaurants" to TableMetadata(1, false),
        "inventory_areas" to TableMetadata(0, false),
        "ingredient_categories" to TableMetadata(0, false),
        "units" to TableMetadata(0, false),
        "ingredients" to TableMetadata(0, false),
        "ingredient_unit_options" to TableMetadata(0, false),
        "suppliers" to TableMetadata(0, false),
        "purchase_receipts" to TableMetadata(0, false),
        "purchase_lines" to TableMetadata(0, false),
        "stock_counts" to TableMetadata(0, false),
        "stock_count_areas" to TableMetadata(0, false),
        "stock_count_lines" to TableMetadata(0, false),
        "waste_events" to TableMetadata(0, false),
        "inventory_movements" to TableMetadata(0, false),
        "inventory_balance_projections" to TableMetadata(0, true),
        "ingredient_cost_projections" to TableMetadata(0, true)
    )

    private fun createValidManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = BackupTestFixtures.RESTAURANT_ID,
        restaurantName = "Test Restaurant",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = validTableMetadata,
        attachments = emptyList(),
        includedSections = listOf("attachments", "data", "preferences"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createValidSnapshot() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(BackupTestFixtures.RESTAURANT_ID, "Test Restaurant", "USD", "en-US", 1000L, 2000L, null)),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )

    private fun createValidPreferences() = BackupPreferencesDto(
        themeMode = "SYSTEM",
        dynamicColorEnabled = true,
        appLocaleTag = "en-US"
    )

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildZipArchive(
        destFile: File,
        manifest: BackupManifest = createValidManifest(),
        snapshot: BackupSnapshotDto = createValidSnapshot(),
        prefs: BackupPreferencesDto = createValidPreferences(),
        extraEntries: Map<String, ByteArray> = emptyMap(),
        overrideChecksumsJson: String? = null,
        omitRequiredEntries: Set<String> = emptySet()
    ) {
        val entryBytes = mutableMapOf<String, ByteArray>()
        val manifestBytes = jsonWriter.encodeToString(manifest).toByteArray()
        val dbBytes = jsonWriter.encodeToString(snapshot).toByteArray()
        val prefsBytes = jsonWriter.encodeToString(prefs).toByteArray()

        if ("manifest.json" !in omitRequiredEntries) entryBytes["manifest.json"] = manifestBytes
        if ("data/database.json" !in omitRequiredEntries) entryBytes["data/database.json"] = dbBytes
        if ("preferences/settings.json" !in omitRequiredEntries) entryBytes["preferences/settings.json"] = prefsBytes

        extraEntries.forEach { (name, bytes) -> entryBytes[name] = bytes }

        val checksumsMap = mutableMapOf<String, String>()
        entryBytes.forEach { (name, bytes) -> checksumsMap[name] = sha256(bytes) }

        val finalChecksumsJson = overrideChecksumsJson ?: jsonWriter.encodeToString(checksumsMap.entries.sortedBy { it.key }.associate { it.key to it.value })

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            entryBytes.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            if ("checksums.json" !in omitRequiredEntries) {
                zos.putNextEntry(ZipEntry("checksums.json"))
                zos.write(finalChecksumsJson.toByteArray())
                zos.closeEntry()
            }
        }
    }

    @Test
    fun validate_acceptsFullyValidArchive() = runBlocking {
        val file = File(context.cacheDir, "valid_archive.zip")
        try {
            buildZipArchive(file)
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Valid::class.java)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsUnexpectedEntry() = runBlocking {
        val file = File(context.cacheDir, "unexpected_entry.zip")
        try {
            buildZipArchive(file, extraEntries = mapOf("unexpected.txt" to "hello".toByteArray()))
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.UNEXPECTED_ENTRY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsMissingRequiredEntry_databaseJson() = runBlocking {
        val file = File(context.cacheDir, "missing_db.zip")
        try {
            buildZipArchive(file, omitRequiredEntries = setOf("data/database.json"))
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsMissingRequiredEntry_manifestJson() = runBlocking {
        val file = File(context.cacheDir, "missing_manifest.zip")
        try {
            buildZipArchive(file, omitRequiredEntries = setOf("manifest.json"))
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsDuplicateChecksumKey() = runBlocking {
        val file = File(context.cacheDir, "duplicate_checksum.zip")
        try {
            val validManifest = createValidManifest()
            val manifestBytes = jsonWriter.encodeToString(validManifest).toByteArray()
            val manifestHash = sha256(manifestBytes)

            val customChecksums = """
                {
                  "manifest.json": "$manifestHash",
                  "manifest\u002ejson": "$manifestHash"
                }
            """.trimIndent()

            buildZipArchive(file, overrideChecksumsJson = customChecksums)
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
        } finally {
            file.delete()
        }
    }

    @Test
    fun archiveEntryValidator_rejectsUnsafeAndAbsolutePaths() {
        assertThat(ArchiveEntryValidator.isSafe("../escaped.txt")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("dir/../escaped.txt")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("/etc/passwd")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("C:\\Windows\\system32")).isFalse()
        assertThat(ArchiveEntryValidator.isSafe("data/database.json")).isTrue()
    }

    @Test
    fun validate_rejectsUnknownFieldInManifest() = runBlocking {
        val file = File(context.cacheDir, "unknown_manifest_field.zip")
        try {
            val manifestJsonWithExtra = """
                {
                  "backupFormatVersion": 1,
                  "createdAtUtc": "2026-01-01T12:00:00Z",
                  "applicationId": "com.miara.cuentame",
                  "appVersionName": "1.0",
                  "appVersionCode": 1,
                  "databaseSchemaVersion": 2,
                  "restaurantId": "${BackupTestFixtures.RESTAURANT_ID}",
                  "restaurantName": "Test Restaurant",
                  "localeTag": "en-US",
                  "currencyCode": "USD",
                  "tableMetadata": {},
                  "attachments": [],
                  "includedSections": ["attachments", "data", "preferences"],
                  "checksumAlgorithm": "SHA-256",
                  "unknownExtraField": "hacker"
                }
            """.trimIndent()

            val destFile = file
            ZipOutputStream(FileOutputStream(destFile)).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJsonWithExtra.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("data/database.json"))
                zos.write(jsonWriter.encodeToString(createValidSnapshot()).toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("preferences/settings.json"))
                zos.write(jsonWriter.encodeToString(createValidPreferences()).toByteArray())
                zos.closeEntry()

                val checksums = mapOf(
                    "manifest.json" to sha256(manifestJsonWithExtra.toByteArray()),
                    "data/database.json" to sha256(jsonWriter.encodeToString(createValidSnapshot()).toByteArray()),
                    "preferences/settings.json" to sha256(jsonWriter.encodeToString(createValidPreferences()).toByteArray())
                )
                zos.putNextEntry(ZipEntry("checksums.json"))
                zos.write(jsonWriter.encodeToString(checksums).toByteArray())
                zos.closeEntry()
            }

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.MANIFEST_INVALID)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsChecksumMismatch() = runBlocking {
        val file = File(context.cacheDir, "checksum_mismatch.zip")
        try {
            val validManifest = createValidManifest()

            val customChecksums = """
                {
                  "manifest.json": "${"f".repeat(64)}",
                  "data/database.json": "${sha256(jsonWriter.encodeToString(createValidSnapshot()).toByteArray())}",
                  "preferences/settings.json": "${sha256(jsonWriter.encodeToString(createValidPreferences()).toByteArray())}"
                }
            """.trimIndent()

            buildZipArchive(file, overrideChecksumsJson = customChecksums)
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.CHECKSUM_MISMATCH)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsSelfReferentialChecksumInManifest() = runBlocking {
        val file = File(context.cacheDir, "self_checksum.zip")
        try {
            val customChecksums = """
                {
                  "manifest.json": "${sha256(jsonWriter.encodeToString(createValidManifest()).toByteArray())}",
                  "data/database.json": "${sha256(jsonWriter.encodeToString(createValidSnapshot()).toByteArray())}",
                  "preferences/settings.json": "${sha256(jsonWriter.encodeToString(createValidPreferences()).toByteArray())}",
                  "checksums.json": "${"a".repeat(64)}"
                }
            """.trimIndent()

            buildZipArchive(file, overrideChecksumsJson = customChecksums)
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsInvalidMovementGraph_missingOriginalReversal() = runBlocking {
        val file = File(context.cacheDir, "bad_reversal.zip")
        try {
            val invalidSnapshot = createValidSnapshot().copy(
                units = listOf(
                    UnitBackupDto("u1", "Kilogram", "kg", "MASS", "1.0", true, 1)
                ),
                inventoryAreas = listOf(
                    InventoryAreaBackupDto("area-1", BackupTestFixtures.RESTAURANT_ID, "Kitchen", "kitchen", 1, true, 1000L, 2000L, null)
                ),
                ingredients = listOf(
                    IngredientBackupDto("ing-1", BackupTestFixtures.RESTAURANT_ID, "Tomato", "tomato", null, "u1", "area-1", "SKU1", "Notes", "10.0", true, 1000L, 2000L, null)
                ),
                inventoryMovements = listOf(
                    InventoryMovementBackupDto(
                        id = "m-rev-bad",
                        restaurantId = BackupTestFixtures.RESTAURANT_ID,
                        ingredientId = "ing-1",
                        areaId = "area-1",
                        movementType = "REVERSAL",
                        quantityBaseSigned = "1.0",
                        unitCostBaseSnapshot = "1.0",
                        totalValueSnapshot = "1.0",
                        effectiveAt = 1000L,
                        sourceDocumentType = "WASTE_EVENT",
                        sourceDocumentId = "we-1",
                        sourceOperationId = "op-1",
                        sourceLineId = null,
                        reversalOfMovementId = "nonexistent-orig-id",
                        createdAt = 1000L
                    )
                )
            )
            val manifest = createValidManifest().let { m ->
                m.copy(
                    tableMetadata = m.tableMetadata + mapOf(
                        "units" to TableMetadata(1, false),
                        "inventory_areas" to TableMetadata(1, false),
                        "ingredients" to TableMetadata(1, false),
                        "inventory_movements" to TableMetadata(1, false)
                    )
                )
            }
            buildZipArchive(file, manifest = manifest, snapshot = invalidSnapshot)
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val invalid = result as BackupValidationResult.Invalid
            assertThat(invalid.code).isEqualTo(BackupValidationCode.SNAPSHOT_INVALID)
            assertThat(invalid.reason).contains("REVERSAL movement target not found")
        } finally {
            file.delete()
        }
    }
}
