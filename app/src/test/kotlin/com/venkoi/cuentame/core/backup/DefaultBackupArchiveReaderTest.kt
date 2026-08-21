package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.*
import com.venkoi.cuentame.core.backup.internal.BackupArchiveFingerprinter
import com.venkoi.cuentame.core.backup.internal.BackupArchiveProcessor
import com.venkoi.cuentame.core.backup.platform.DefaultBackupArchiveReader
import com.venkoi.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
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
    private val defaultProcessor = BackupArchiveProcessor(BackupReadLimits(), BackupZipInputFactory { input -> ZipInputStream(input) })
    private val fingerprinter = BackupArchiveFingerprinter(jsonCodecs)

    @Before
    fun setup() {
        reader = DefaultBackupArchiveReader(jsonCodecs, defaultProcessor, fingerprinter)
    }

    private fun createCustomReader(limits: BackupReadLimits = BackupReadLimits(), factory: BackupZipInputFactory = BackupZipInputFactory { input -> ZipInputStream(input) }): DefaultBackupArchiveReader {
        return DefaultBackupArchiveReader(jsonCodecs, BackupArchiveProcessor(limits, factory), fingerprinter)
    }

    @Test
    fun `inspect schema 2 archive succeeds and excludes recipes`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = buildManifest().copy(databaseSchemaVersion = 2)
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = result as BackupArchiveInspectionResult.Ready
        assertThat(ready.archive.manifest.databaseSchemaVersion).isEqualTo(2)
        assertThat(ready.archive.snapshot.preparationRecipes).isEmpty()
    }

    @Test
    fun `inspect schema 2 manifest with schema 3 metadata fails`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = buildManifest().copy(
            databaseSchemaVersion = 2,
            tableMetadata = buildManifest().tableMetadata + ("preparation_recipes" to com.venkoi.cuentame.core.model.backup.TableMetadata(0, false))
        )
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun `inspect schema 3 manifest missing recipe metadata fails`() = runTest {
        val builder = BackupArchiveTestBuilder(jsonCodecs)
        val manifest = buildManifest().copy(
            databaseSchemaVersion = 3,
            tableMetadata = buildManifest().tableMetadata.filterKeys { it != "preparation_recipes" }
        )
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MalformedManifest)
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
        val customReader = createCustomReader(limits = smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build() // DB JSON > 5 bytes
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.EntryLimitExceeded)
    }

    @Test
    fun `inspect with total size limit exceeded fails`() = runTest {
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 100L)
        val customReader = createCustomReader(limits = smallLimits)
        
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
    fun `inspect enforces total archive limit while streaming`() = runTest {
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 10L)
        val customReader = createCustomReader(limits = smallLimits)
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = customReader.inspect(ByteArrayInputStream(bytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.TotalLimitExceeded)
    }

    @Test
    fun `inspect caller owned stream remains open`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        reader.inspect(input, docUri)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspect caller owned stream remains open after success`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        val result = reader.inspect(input, docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspect caller owned stream remains open after failure`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).removeEntry("manifest.json").build()
        val input = TrackingInputStream(ByteArrayInputStream(bytes))
        
        val result = reader.inspect(input, docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat(input.isClosed).isFalse()
    }

    @Test
    fun `inspected archive performs defensive copies`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri) as BackupArchiveInspectionResult.Ready
        val archive = result.archive
        
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            (archive.snapshot.restaurants as MutableList).clear()
        }
    }

    @Test
    fun `inspect enforces core entry limit while streaming`() = runTest {
        val smallLimits = BackupReadLimits(maxDatabaseJsonBytes = 10L)
        val customReader = createCustomReader(limits = smallLimits)
        
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
        
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.UnexpectedEntry)
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
        val mJson = jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.model.backup.BackupManifest.serializer(), manifest).toByteArray()
        builder.addEntry("manifest.json", mJson)
        builder.addEntry("data/database.json", jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.backup.model.BackupSnapshotDto.serializer(), createValidEmptySnapshot()).toByteArray())
        builder.addEntry("preferences/settings.json", jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.model.backup.BackupPreferencesDto.serializer(), com.venkoi.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")).toByteArray())
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
    fun `inspect with checksums json self-reference fails`() = runTest {
        val manifest = buildManifest()
        val mJson = jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.model.backup.BackupManifest.serializer(), manifest).toByteArray()
        val dbJson = jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.backup.model.BackupSnapshotDto.serializer(), createValidEmptySnapshot()).toByteArray()
        val pJson = jsonCodecs.writer.encodeToString(com.venkoi.cuentame.core.model.backup.BackupPreferencesDto.serializer(), com.venkoi.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")).toByteArray()
        
        val rawChecksums = mapOf(
            "manifest.json" to "a".repeat(64),
            "data/database.json" to "a".repeat(64),
            "preferences/settings.json" to "a".repeat(64),
            "checksums.json" to "a".repeat(64) // Self-reference
        )
        val cJson = jsonCodecs.writer.encodeToString(rawChecksums).toByteArray()
        
        val bytes = BackupArchiveTestBuilder(jsonCodecs)
            .replaceFirstEntry("manifest.json", mJson)
            .replaceFirstEntry("data/database.json", dbJson)
            .replaceFirstEntry("preferences/settings.json", pJson)
            .replaceFirstEntry("checksums.json", cJson)
            .build()
            
        val result = reader.inspect(ByteArrayInputStream(bytes), docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MalformedChecksums)
    }

    @Test
    fun `inspect ensures ZipInputStream closed but source remains open`() = runTest {
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
        val customReader = createCustomReader(factory = factory)
        
        customReader.inspect(source, docUri)
        
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
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
        val customReader = createCustomReader(factory = factory)
        
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
        val customReader = createCustomReader(factory = factory)
        
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
        val customReader = createCustomReader(factory = factory)
        
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
        val customReader = createCustomReader(factory = factory)
        
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
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    zipCloseCount++
                    throw IOException("Close failed")
                }
            }
        }
        val customReader = createCustomReader(factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.GenericIo)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `primary typed failure is preserved when ZipInputStream close also fails`() = runTest {
        // Use duplicate entry to cause failure DURING the loop
        val bytes = BackupArchiveTestBuilder(jsonCodecs).addDuplicateEntry("data/database.json", "{}".toByteArray()).build()
        val source = TrackingInputStream(ByteArrayInputStream(bytes))
        var zipCloseCount = 0
        val factory = BackupZipInputFactory { input ->
            object : ZipInputStream(input) {
                override fun close() {
                    zipCloseCount++
                    throw IOException("Close failed")
                }
            }
        }
        val customReader = createCustomReader(factory = factory)
        
        val result = customReader.inspect(source, docUri)
        
        // Assert primary failure preserved
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.DuplicateEntry)
        
        assertThat(zipCloseCount).isEqualTo(1)
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
        val customReader = createCustomReader(factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.MissingCoreEntry)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
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
        val customReader = createCustomReader(factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.DuplicateEntry)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `inspect with unsafe entry path closes exactly once`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).addEntry("../unsafe", byteArrayOf(0)).build()
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
        val customReader = createCustomReader(factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.UnsafeEntryPath)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `inspect with entry limit exceeded closes exactly once`() = runTest {
        val smallLimits = BackupReadLimits(maxDatabaseJsonBytes = 1L)
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
        val customReader = createCustomReader(limits = smallLimits, factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.EntryLimitExceeded)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `inspect with total archive limit exceeded closes exactly once`() = runTest {
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 1L)
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
        val customReader = createCustomReader(limits = smallLimits, factory = factory)
        
        val result = customReader.inspect(source, docUri)
        assertThat((result as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.TotalLimitExceeded)
        assertThat(zipCloseCount).isEqualTo(1)
        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `inspect valid attachment-bearing archive returns Ready and Eligible`() = runTest {
        val fixture = BackupTestFixtures.createValidAttachmentArchiveFixture(jsonCodecs)
        
        val result = reader.inspect(ByteArrayInputStream(fixture.archiveBytes), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = result as BackupArchiveInspectionResult.Ready
        assertThat(ready.archive.manifest.attachments).isNotEmpty()
        assertThat(ready.archive.manifest.attachments[0].attachmentId).isEqualTo(fixture.attachmentId)
        assertThat(ready.eligibility).isEqualTo(com.venkoi.cuentame.core.model.backup.BackupRestoreEligibility.Eligible)
    }

    private fun createValidEmptySnapshot() = com.venkoi.cuentame.core.backup.model.BackupSnapshotDto(
        restaurants = listOf(com.venkoi.cuentame.core.backup.model.RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 0, 0, null)),
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

    private fun buildManifest(): com.venkoi.cuentame.core.model.backup.BackupManifest {
        val tables = BackupFormatV1Contract.expectedTablesForSchema(2)
            .associateWith { com.venkoi.cuentame.core.model.backup.TableMetadata(if (it == "restaurants") 1 else 0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        return com.venkoi.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract.BACKUP_FORMAT_VERSION,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.cuentame",
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
