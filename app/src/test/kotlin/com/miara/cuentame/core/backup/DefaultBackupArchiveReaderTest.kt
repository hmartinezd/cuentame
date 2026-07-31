package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveReader
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

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
    fun `inspect with missing core entry fails`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .removeEntry("data/database.json")
            .recomputeAllChecksums()
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.MissingCoreEntry)
    }

    @Test
    fun `inspect with duplicate entry fails`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .addDuplicateEntry("data/database.json", "{}".toByteArray())
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.DuplicateEntry)
    }

    @Test
    fun `inspect with unsafe entry path fails`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("../outside.json", "{}".toByteArray())
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.UnsafeEntryPath)
    }

    @Test
    fun `inspect with checksum mismatch fails`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        builder.replaceFirstEntry("data/database.json", "corrupted".toByteArray())
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.ChecksumMismatch)
    }

    @Test
    fun `inspect with oversized entry fails`() = runTest {
        val smallLimits = BackupReadLimits(maxDatabaseJsonBytes = 5L)
        val customReader = DefaultBackupArchiveReader(jsonCodecs, smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build() // DB JSON > 5 bytes
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.EntryLimitExceeded)
    }

    @Test
    fun `inspect with total size limit exceeded fails`() = runTest {
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 100L)
        val customReader = DefaultBackupArchiveReader(jsonCodecs, smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.TotalLimitExceeded)
    }

    @Test
    fun `inspect with incompatible schema version fails`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = buildManifest().copy(databaseSchemaVersion = 99)
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.IncompatibleSchemaVersion)
    }

    @Test
    fun `inspect caller owned stream remains open`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        reader.inspect(input, docUri)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspect enforces core entry limit while streaming`() = runTest {
        val smallLimits = BackupReadLimits(maxDatabaseJsonBytes = 10L)
        val customReader = DefaultBackupArchiveReader(jsonCodecs, smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.EntryLimitExceeded)
    }

    @Test
    fun `inspect rejects directory entries`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("some_dir/", ByteArray(0))
            .build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.UnsafeEntryPath)
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
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        builder.removeEntry("manifest.json")
        builder.removeEntry("data/database.json")
        builder.removeEntry("preferences/settings.json")
        builder.removeEntry("checksums.json")
        
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
        
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.snapshot.restaurants as MutableList).clear()
        }
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.manifest.includedSections as MutableList).clear()
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
    fun `ZipInputStream closes exactly once after success`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        var zipCloseCount = 0
        
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `ZipInputStream closes exactly once after failure`() = runTest {
        val source = TrackingInputStream(ByteArrayInputStream(ByteArray(0)))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun getNextEntry(): java.util.zip.ZipEntry? = throw IOException("Read error")
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `ZipInputStream closes exactly once after cancellation`() = runTest {
        val source = TrackingInputStream(ByteArrayInputStream(ByteArray(0)))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun getNextEntry(): java.util.zip.ZipEntry? = throw CancellationException()
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        try {
            customReader.inspect(source, docUri)
            org.junit.Assert.fail("Should have thrown CancellationException")
        } catch (e: CancellationException) {
            // Success
        }
        
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `closeEntry failure cannot produce Ready`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun closeEntry() {
                    throw IOException("Close entry failed")
                }
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.InvalidZip)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `ZipInputStream close failure cannot produce Ready`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    throw IOException("Close failed")
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.GenericIo)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `inspect with missing core entry closes exactly once`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).removeEntry("data/database.json").build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MissingCoreEntry)
        assertThat(zipCloseCount).isEqualTo(1)
    }

    @Test
    fun `inspect with duplicate entry closes exactly once`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).addDuplicateEntry("data/database.json", "{}".toByteArray()).build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    zipCloseCount++
                    super.close()
                }
            }
        }
        val customReader = DefaultBackupArchiveReader(jsonCodecs, BackupReadLimits(), factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.DuplicateEntry)
        assertThat(zipCloseCount).isEqualTo(1)
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
