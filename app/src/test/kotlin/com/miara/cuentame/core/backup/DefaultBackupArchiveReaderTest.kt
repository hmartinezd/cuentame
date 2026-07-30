package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveReader
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class DefaultBackupArchiveReaderTest {

    private val jsonCodecs = BackupJsonCodecs()
    private val docUri = BackupDocumentUri("file:///test.zip")
    private lateinit var reader: DefaultBackupArchiveReader

    @Before
    fun setup() {
        reader = DefaultBackupArchiveReader(jsonCodecs)
    }

    @Test
    fun `inspect valid minimal archive succeeds`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = result as BackupArchiveInspectionResult.Ready
        assertThat(ready.preview.restaurantName).isEqualTo("Test Rest")
        assertThat(ready.archive.attachmentSummaries).isEmpty()
    }

    @Test
    fun `inspect enforces core entry limit while streaming`() = runTest {
        val smallLimits = BackupReadLimits(maxDatabaseJsonBytes = 10L)
        val customReader = DefaultBackupArchiveReader(jsonCodecs, smallLimits)
        
        // Default DB JSON is larger than 10 bytes
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.EntryLimitExceeded)
    }

    @Test
    fun `inspect enforces total archive limit while streaming`() = runTest {
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 100L)
        val customReader = DefaultBackupArchiveReader(jsonCodecs, smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.TotalLimitExceeded)
    }

    @Test
    fun `inspect rejects directory entries`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("some_dir/", ByteArray(0)) // isDirectory = true based on trailing slash for some tools, but builder needs to be sure
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.UnsafeEntryPath)
    }

    @Test
    fun `inspect caller owned stream remains open after success`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        reader.inspect(input, docUri)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspect caller owned stream remains open after failure`() = runTest {
        val bytes = "not a zip".toByteArray()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        reader.inspect(input, docUri)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspected archive performs defensive copies`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        val ready = result as BackupArchiveInspectionResult.Ready
        val snapshot = ready.archive.snapshot
        
        // Collections should be unmodifiable
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.restaurants as MutableList).clear()
        }
    }

    @Test
    fun `inspect with malformed checksums fails`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceRawChecksums("{invalid}")
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MalformedChecksums)
    }

    @Test
    fun `inspect with missing core checksum fails`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceRawChecksums("{\"manifest.json\":\"${"a".repeat(64)}\"}")
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.ChecksumMismatch)
    }

    @Test
    fun `inspect with unsupported format version fails`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = buildManifest().copy(backupFormatVersion = 99)
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.UnsupportedFormatVersion)
    }

    @Test
    fun `inspect core entries accepted in any order`() = runTest {
        // Build ZIP with core entries in "wrong" order (Manifest first, then DB, etc.)
        // builder.build() uses a specific order but let's see if we can easily change it.
        // Actually builder.entries is private but I can use addEntry.
        
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        builder.removeEntry("manifest.json")
        builder.removeEntry("data/database.json")
        builder.removeEntry("preferences/settings.json")
        builder.removeEntry("checksums.json")
        
        // Add manifest first
        val manifest = buildManifest()
        val mJson = jsonCodecs.writer.encodeToString(com.miara.cuentame.core.model.backup.BackupManifest.serializer(), manifest).toByteArray()
        builder.addEntry("manifest.json", mJson)
        builder.addEntry("data/database.json", jsonCodecs.writer.encodeToString(com.miara.cuentame.core.backup.model.BackupSnapshotDto.serializer(), createValidEmptySnapshot()).toByteArray())
        builder.addEntry("preferences/settings.json", jsonCodecs.writer.encodeToString(com.miara.cuentame.core.model.backup.BackupPreferencesDto.serializer(), com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")).toByteArray())
        builder.recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
    }

    @Test
    fun `inspected archive deep immutability check`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri) as BackupArchiveInspectionResult.Ready
        val archive = result.archive
        
        // 1. Snapshot collections
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.snapshot.restaurants as MutableList).clear()
        }
        
        // 2. Manifest collections
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.manifest.includedSections as MutableList).clear()
        }
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.manifest.tableMetadata as MutableMap).clear()
        }
    }

    private fun createValidEmptySnapshot() = com.miara.cuentame.core.backup.model.BackupSnapshotDto(
        restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 0, 0, null)),
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

    private fun buildManifest(): com.miara.cuentame.core.model.backup.BackupManifest {
        val tables = BackupFormatV1Contract.EXPECTED_TABLES
            .associateWith { com.miara.cuentame.core.model.backup.TableMetadata(if (it == "restaurants") 1 else 0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        return com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = "rest-1",
            restaurantName = "Test Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = tables,
            attachments = emptyList(),
            includedSections = listOf("attachments", "data", "preferences")
        )
    }

    private class TrackingInputStream(delegate: InputStream) : java.io.FilterInputStream(delegate) {
        var isClosed = false
        override fun close() {
            isClosed = true
            super.close()
        }
    }
}
