package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.backup.model.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TestZipEntry(
    val name: String,
    val bytes: ByteArray,
    val timestamp: Long = 0L
)

class BackupArchiveTestBuilder(
    private val jsonCodecs: BackupJsonCodecs = BackupJsonCodecs()
) {
    private val entries = mutableListOf<TestZipEntry>()

    init {
        // Build valid default state
        val manifest = createValidBaseManifest()
        val settings = BackupPreferencesDto("SYSTEM", true, "en-US")
        val snapshot = createValidEmptySnapshotDto()
        
        val manifestJson = jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        val settingsJson = jsonCodecs.writer.encodeToString(BackupPreferencesDto.serializer(), settings).toByteArray()
        val dbJson = jsonCodecs.writer.encodeToString(BackupSnapshotDto.serializer(), snapshot).toByteArray()
        
        val checksums = mapOf(
            "data/database.json" to hash(dbJson),
            "preferences/settings.json" to hash(settingsJson),
            "manifest.json" to hash(manifestJson)
        )
        val checksumsJson = serializeChecksums(checksums)

        // Exact contract order: DB, Settings, (Attachments), Manifest, Checksums
        entries.add(TestZipEntry("data/database.json", dbJson))
        entries.add(TestZipEntry("preferences/settings.json", settingsJson))
        entries.add(TestZipEntry("manifest.json", manifestJson))
        entries.add(TestZipEntry("checksums.json", checksumsJson))
    }

    fun addEntry(name: String, content: ByteArray) = apply {
        entries.add(TestZipEntry(name, content))
    }

    fun addDuplicateEntry(name: String, content: ByteArray) = apply {
        entries.add(TestZipEntry(name, content))
    }

    fun removeEntry(name: String) = apply {
        entries.removeIf { it.name == name }
    }

    fun replaceFirstEntry(name: String, content: ByteArray) = apply {
        val idx = entries.indexOfFirst { it.name == name }
        if (idx != -1) {
            entries[idx] = TestZipEntry(name, content)
        }
    }

    fun replaceManifest(manifest: BackupManifest) = apply {
        val json = jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        replaceFirstEntry("manifest.json", json)
    }

    fun replaceRawManifest(json: String) = apply {
        replaceFirstEntry("manifest.json", json.toByteArray())
    }

    fun replaceRawPreferences(json: String) = apply {
        replaceFirstEntry("preferences/settings.json", json.toByteArray())
    }

    fun replaceRawDatabase(json: String) = apply {
        replaceFirstEntry("data/database.json", json.toByteArray())
    }

    fun replaceRawChecksums(json: String) = apply {
        replaceFirstEntry("checksums.json", json.toByteArray())
    }

    fun recomputeAllChecksums() = apply {
        val checksums = mutableMapOf<String, String>()
        entries.forEach { 
            if (it.name != "checksums.json") {
                checksums[it.name] = hash(it.bytes)
            }
        }
        val json = serializeChecksums(checksums)
        replaceFirstEntry("checksums.json", json)
    }

    fun build(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { entry ->
                zos.putNextEntry(ZipEntry(entry.name).apply { time = entry.timestamp })
                zos.write(entry.bytes)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun serializeChecksums(map: Map<String, String>): ByteArray {
        val serializer = MapSerializer(String.serializer(), String.serializer())
        return jsonCodecs.writer.encodeToString(serializer, map.toSortedMap()).toByteArray()
    }

    private fun createValidBaseManifest() = BackupManifest(
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
        tableMetadata = createValidTableMetadata(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments").sorted()
    )

    private fun createValidEmptySnapshotDto() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 0, 0, null)),
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

    private fun createValidTableMetadata() = mapOf(
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
    ).entries.sortedBy { it.key }.associate { it.key to it.value }
}
