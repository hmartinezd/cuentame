package com.miara.cuentame.core.backup.internal

import androidx.core.util.AtomicFile
import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.backup.api.RestorePhase
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RestoreJournalDto(
    val sessionId: String,
    val phase: RestorePhase,
    val expectedArchiveFingerprint: String,
    val previousPreferences: BackupPreferencesDto? = null,
    val startedAt: Long
)

sealed interface RestoreJournalReadResult {
    data object Absent : RestoreJournalReadResult
    data class Present(val journal: RestoreJournalDto) : RestoreJournalReadResult
    data object Corrupt : RestoreJournalReadResult
}

@Singleton
class RestoreJournal @Inject constructor(
    private val storage: InternalBackupRestoreStorage,
    private val codecs: BackupJsonCodecs
) {
    fun read(): RestoreJournalReadResult {
        val file = storage.getJournalFile()
        if (!file.exists()) return RestoreJournalReadResult.Absent
        
        return try {
            val json = file.readText()
            val dto = codecs.reader.decodeFromString<RestoreJournalDto>(json)
            RestoreJournalReadResult.Present(dto)
        } catch (e: Exception) {
            RestoreJournalReadResult.Corrupt
        }
    }

    fun write(dto: RestoreJournalDto) {
        val file = storage.getJournalFile()
        val atomicFile = AtomicFile(file)
        val fos = atomicFile.startWrite()
        try {
            val json = codecs.writer.encodeToString(dto)
            fos.write(json.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            throw e
        }
    }

    fun delete() {
        storage.getJournalFile().delete()
    }
}
