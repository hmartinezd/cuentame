package com.venkoi.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupJsonCodecs
import com.venkoi.cuentame.core.backup.api.RestorePhase
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreJournalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val storage = mockk<InternalBackupRestoreStorage>()
    private val codecs = BackupJsonCodecs()
    private lateinit var journal: RestoreJournal
    private lateinit var journalFile: File

    @Before
    fun setup() {
        journalFile = File(tempFolder.root, "journal.json")
        every { storage.getJournalFile() } returns journalFile
        journal = RestoreJournal(storage, codecs)
    }

    @Test
    fun `journal absent`() {
        assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
    }

    @Test
    fun `journal valid`() {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, emptyList(), 123L)
        journal.write(dto)
        
        val result = journal.read()
        assertThat(result).isInstanceOf(RestoreJournalReadResult.Present::class.java)
        assertThat((result as RestoreJournalReadResult.Present).journal).isEqualTo(dto)
    }

    @Test
    fun `journal corrupt`() {
        journalFile.writeText("invalid json")
        assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Corrupt)
    }

    @Test
    fun `journal atomic write succeeds`() {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, emptyList(), 123L)
        journal.write(dto)
        assertThat(journalFile.exists()).isTrue()
    }

    @Test
    fun `journal delete removes durable state`() {
        val dto = RestoreJournalDto("session", RestorePhase.COMPLETED, "hash", null, emptyList(), 123L)
        journal.write(dto)
        assertThat(journalFile.exists()).isTrue()
        
        journal.delete()
        assertThat(journalFile.exists()).isFalse()
    }
}
