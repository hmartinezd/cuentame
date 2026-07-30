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
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    fun addRawEntry(name: String, content: ByteArray) = apply {
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

    /**
     * Builds a ZIP manually to allow duplicate entry names.
     * Uses STORED (no compression) for simplicity in manual construction.
     */
    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        val entryOffsets = mutableListOf<Long>()
        
        // 1. Local File Headers + Data
        for (entry in entries) {
            entryOffsets.add(out.size().toLong())
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val crc = crc32(entry.bytes)
            
            // Signature: 0x04034b50
            out.writeLeInt(0x04034b50)
            // Version: 10
            out.writeLeShort(10)
            // Flags: 0
            out.writeLeShort(0)
            // Compression: 0 (Stored)
            out.writeLeShort(0)
            // Time/Date: 0
            out.writeLeInt(0)
            // CRC-32
            out.writeLeInt(crc.toInt())
            // Compressed size
            out.writeLeInt(entry.bytes.size)
            // Uncompressed size
            out.writeLeInt(entry.bytes.size)
            // Name length
            out.writeLeShort(nameBytes.size)
            // Extra length: 0
            out.writeLeShort(0)
            
            out.write(nameBytes)
            out.write(entry.bytes)
        }
        
        val centralDirectoryOffset = out.size().toLong()
        
        // 2. Central Directory Headers
        for (i in entries.indices) {
            val entry = entries[i]
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val crc = crc32(entry.bytes)
            
            // Signature: 0x02014b50
            out.writeLeInt(0x02014b50)
            // Version made by
            out.writeLeShort(20)
            // Version needed
            out.writeLeShort(10)
            // Flags
            out.writeLeShort(0)
            // Compression
            out.writeLeShort(0)
            // Time/Date
            out.writeLeInt(0)
            // CRC-32
            out.writeLeInt(crc.toInt())
            // Compressed size
            out.writeLeInt(entry.bytes.size)
            // Uncompressed size
            out.writeLeInt(entry.bytes.size)
            // Name length
            out.writeLeShort(nameBytes.size)
            // Extra length
            out.writeLeShort(0)
            // Comment length
            out.writeLeShort(0)
            // Disk number start
            out.writeLeShort(0)
            // Internal attributes
            out.writeLeShort(0)
            // External attributes
            out.writeLeInt(0)
            // Relative offset of local header
            out.writeLeInt(entryOffsets[i].toInt())
            
            out.write(nameBytes)
        }
        
        val centralDirectorySize = out.size().toLong() - centralDirectoryOffset
        
        // 3. End of Central Directory Record
        // Signature: 0x06054b50
        out.writeLeInt(0x06054b50)
        // Number of this disk
        out.writeLeShort(0)
        // Disk where central directory starts
        out.writeLeShort(0)
        // Number of central directory records on this disk
        out.writeLeShort(entries.size)
        // Total number of central directory records
        out.writeLeShort(entries.size)
        // Size of central directory
        out.writeLeInt(centralDirectorySize.toInt())
        // Offset of start of central directory
        out.writeLeInt(centralDirectoryOffset.toInt())
        // ZIP file comment length
        out.writeLeShort(0)
        
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLeInt(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLeShort(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
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
