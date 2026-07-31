package com.miara.cuentame.core.backup.internal

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
    val stagingDirPath: String,
    val rollbackDirPath: String,
    val previousPreferences: BackupPreferencesDto? = null,
    val startedAt: Long
)

@Singleton
class RestoreJournal @Inject constructor(
    private val storage: InternalBackupRestoreStorage,
    private val codecs: BackupJsonCodecs
) {
    fun read(): RestoreJournalDto? {
        val file = storage.getJournalFile()
        if (!file.exists()) return null
        return try {
            codecs.reader.decodeFromString<RestoreJournalDto>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun write(dto: RestoreJournalDto) {
        val file = storage.getJournalFile()
        val tempFile = File(file.absolutePath + ".tmp")
        try {
            val json = codecs.writer.encodeToString(dto)
            tempFile.writeText(json)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    fun delete() {
        storage.getJournalFile().delete()
    }
}
