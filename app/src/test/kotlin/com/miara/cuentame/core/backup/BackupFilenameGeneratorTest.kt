package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class BackupFilenameGeneratorTest {

    @Test
    fun `generates valid name with restaurant`() {
        val now = Instant.parse("2026-07-29T12:00:00Z")
        val name = BackupFilenameGenerator.generate("My Restaurant", now)
        assertThat(name).isEqualTo("Cuentame_My_Restaurant_2026-07-29_1200.cuentame-backup")
    }

    @Test
    fun `generates valid name without restaurant`() {
        val now = Instant.parse("2026-07-29T12:00:00Z")
        val name = BackupFilenameGenerator.generate(null, now)
        assertThat(name).isEqualTo("Cuentame_Backup_2026-07-29_1200.cuentame-backup")
    }

    @Test
    fun `sanitizes restaurant name`() {
        val now = Instant.parse("2026-07-29T12:00:00Z")
        val name = BackupFilenameGenerator.generate("Rest/Name*", now)
        assertThat(name).isEqualTo("Cuentame_Rest_Name_2026-07-29_1200.cuentame-backup")
    }
}
