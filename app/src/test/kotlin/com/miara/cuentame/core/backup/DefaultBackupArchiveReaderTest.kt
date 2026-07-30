package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveReader
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

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
        // Don't recompute checksums
        
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
        val smallLimits = BackupReadLimits(maxTotalUncompressedBytes = 10L)
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
        val manifest = builder.buildManifest().copy(databaseSchemaVersion = 99)
        builder.replaceManifest(manifest).recomputeAllChecksums()
        
        val result = reader.inspect(ByteArrayInputStream(builder.build()), docUri)
        
        assertThat(result).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        val failure = result as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.IncompatibleSchemaVersion)
    }

    @Test
    fun `inspect caller owned stream remains open`() = runTest {
        val bytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val input = object : ByteArrayInputStream(bytes) {
            var isClosed = false
            override fun close() {
                isClosed = true
                super.close()
            }
        }
        
        reader.inspect(input, docUri)
        assertThat(input.isClosed).isFalse()
    }

    // Helper to extract manifest from builder for mutation
    private fun BackupArchiveTestBuilder.buildManifest(): com.miara.cuentame.core.model.backup.BackupManifest {
        // This is a bit hacky but works for unit tests without exposing builder internals too much
        // Re-implementing a small part of builder logic to get the manifest
        return jsonCodecs.reader.decodeFromString(
            com.miara.cuentame.core.model.backup.BackupManifest.serializer(),
            "{\"backupFormatVersion\":1,\"createdAtUtc\":\"2026-01-01T12:00:00Z\",\"applicationId\":\"com.miara.cuentame\",\"appVersionName\":\"1.0\",\"appVersionCode\":1,\"databaseSchemaVersion\":2,\"restaurantId\":\"rest-1\",\"restaurantName\":\"Test Rest\",\"localeTag\":\"en-US\",\"currencyCode\":\"USD\",\"tableMetadata\":{},\"attachments\":[],\"includedSections\":[\"attachments\",\"data\",\"preferences\"],\"checksumAlgorithm\":\"SHA-256\"}"
        )
    }
}
