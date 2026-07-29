package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.model.backup.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveValidatorAdversarialTest {

    private val jsonCodecs = BackupJsonCodecs()
    private lateinit var validator: DefaultBackupArchiveValidator

    @Before
    fun setup() {
        validator = DefaultBackupArchiveValidator(jsonCodecs)
    }

    @Test
    fun `positive control - valid archive passes`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs).build()
        val result = validator.validate(ByteArrayInputStream(zipBytes))
        assertThat(result).isInstanceOf(BackupValidationResult.Valid::class.java)
    }

    @Test
    fun `rejects archive with missing manifest`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .removeEntry("manifest.json")
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.MISSING_REQUIRED_ENTRY)
    }

    @Test
    fun `rejects archive with unexpected entry`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("unexpected.txt", "hacker".toByteArray())
            .recomputeAllChecksums()
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNEXPECTED_ENTRY)
    }

    @Test
    fun `rejects archive with checksum key mismatch`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            // Manual corruption of checksums JSON key set
            .replaceRawChecksums("{\"data/database.json\":\"hash\"}")
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_KEY_SET_MISMATCH)
    }

    @Test
    fun `rejects archive with checksum hash mismatch`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            // Valid JSON but wrong hash
            .replaceRawChecksums("{\"data/database.json\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"manifest.json\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"preferences/settings.json\":\"0000000000000000000000000000000000000000000000000000000000000000\"}")
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun `rejects archive with overlong entry name`() {
        val longName = "a".repeat(BackupLimits.MAX_ENTRY_NAME_LENGTH_BYTES + 1)
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry(longName, "data".toByteArray())
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNSAFE_ENTRY_PATH)
    }

    @Test
    fun `rejects archive with unsafe relative path`() {
        val zipBytes = BackupArchiveTestBuilder(jsonCodecs)
            .addEntry("../outside.json", "data".toByteArray())
            .build()
            
        val result = validator.validate(ByteArrayInputStream(zipBytes)) as BackupValidationResult.Invalid
        assertThat(result.code).isEqualTo(BackupValidationCode.UNSAFE_ENTRY_PATH)
    }
}
