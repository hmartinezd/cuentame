package com.venkoi.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupJsonCodecs
import com.venkoi.cuentame.core.model.backup.BackupManifest
import com.venkoi.cuentame.core.model.backup.TableMetadata
import org.junit.Test

class BackupArchiveFingerprinterTest {

    private val fingerprinter = BackupArchiveFingerprinter(BackupJsonCodecs())

    @Test
    fun `calculate produces stable fingerprint for same inputs`() {
        val manifest = createBaseManifest()
        val checksums = mapOf("file1" to "hash1", "file2" to "hash2")
        
        val f1 = fingerprinter.calculate(manifest, checksums)
        val f2 = fingerprinter.calculate(manifest, checksums)
        
        assertThat(f1).isEqualTo(f2)
    }

    @Test
    fun `calculate produces different fingerprint for different checksums`() {
        val manifest = createBaseManifest()
        val c1 = mapOf("file1" to "hash1")
        val c2 = mapOf("file1" to "hash2")
        
        val f1 = fingerprinter.calculate(manifest, c1)
        val f2 = fingerprinter.calculate(manifest, c2)
        
        assertThat(f1).isNotEqualTo(f2)
    }

    @Test
    fun `calculate is independent of checksum key order`() {
        val manifest = createBaseManifest()
        val c1 = mapOf("a" to "1", "b" to "2")
        val c2 = mapOf("b" to "2", "a" to "1")
        
        val f1 = fingerprinter.calculate(manifest, c1)
        val f2 = fingerprinter.calculate(manifest, c2)
        
        assertThat(f1).isEqualTo(f2)
    }

    private fun createBaseManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T00:00:00Z",
        applicationId = "com.venkoi.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = "r1",
        restaurantName = "N",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = emptyList()
    )
}
