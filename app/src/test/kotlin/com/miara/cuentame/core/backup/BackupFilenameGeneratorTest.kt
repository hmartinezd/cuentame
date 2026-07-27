package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class BackupFilenameGeneratorTest {

    @Test
    fun `generate uses restaurant name and timestamp`() {
        val timestamp = Instant.parse("2026-01-01T12:34:56Z")
        val filename = BackupFilenameGenerator.generate("My Restaurant", timestamp)
        assertThat(filename).isEqualTo("Cuentame_My_Restaurant_2026-01-01_1234.cuentame-backup")
    }

    @Test
    fun `generate sanitizes restaurant name`() {
        val timestamp = Instant.parse("2026-01-01T12:00:00Z")
        val filename = BackupFilenameGenerator.generate("Rest#1 / &More", timestamp)
        assertThat(filename).isEqualTo("Cuentame_Rest_1_More_2026-01-01_1200.cuentame-backup")
    }

    @Test
    fun `generate handles null or blank restaurant name`() {
        val timestamp = Instant.parse("2026-01-01T12:00:00Z")
        assertThat(BackupFilenameGenerator.generate(null, timestamp))
            .isEqualTo("Cuentame_Backup_2026-01-01_1200.cuentame-backup")
        assertThat(BackupFilenameGenerator.generate("   ", timestamp))
            .isEqualTo("Cuentame_Backup_2026-01-01_1200.cuentame-backup")
    }
}
