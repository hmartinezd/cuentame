package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.model.RestaurantBackupDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
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
        restaurantId = "rest-1",
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = validTableMetadata,
        attachments = emptyList(),
        includedSections = listOf("attachments", "data", "preferences"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createValidSnapshot() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 100, 200, null)),
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
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
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
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Unexpected")
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsMissingRequiredEntry() = runBlocking {
        val file = File(context.cacheDir, "missing_db.zip")
        try {
            buildZipArchive(file, omitRequiredEntries = setOf("data/database.json"))
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Missing data/database.json")
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
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Duplicate key detected")
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsUnsafePathTraversal() = runBlocking {
        val file = File(context.cacheDir, "path_traversal.zip")
        try {
            buildZipArchive(file, extraEntries = mapOf("../escaped.txt" to "evil".toByteArray()))
            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Unsafe entry name")
        } finally {
            file.delete()
        }
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
                  "restaurantId": "rest-1",
                  "restaurantName": "Test Rest",
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
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Malformed manifest.json")
        } finally {
            file.delete()
        }
    }
}
