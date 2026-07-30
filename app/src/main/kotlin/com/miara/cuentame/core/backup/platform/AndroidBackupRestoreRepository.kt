package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupRestoreRepository @Inject constructor(
    private val documentStore: BackupDocumentStore,
    private val archiveReader: BackupArchiveReader
) : BackupRestoreRepository {

    override suspend fun inspect(source: BackupDocumentUri): BackupArchiveInspectionResult {
        return try {
            documentStore.openForRead(source).use { input ->
                archiveReader.inspect(input, source)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.PermissionDenied)
        } catch (e: BackupDocumentOpenException) {
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.SourceUnavailable)
        } catch (e: Exception) {
            BackupArchiveInspectionResult.Failure(BackupRestoreFailure.GenericIo)
        }
    }
}
