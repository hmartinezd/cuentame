package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.backup.BackupMapper
import com.miara.cuentame.core.backup.ChecksumProvider
import com.miara.cuentame.core.backup.api.AttachmentSourceUri
import com.miara.cuentame.core.backup.api.BackupSnapshotResult
import com.miara.cuentame.core.backup.api.BackupSnapshotSource
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.database.dao.BackupDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBackupSnapshotSource @Inject constructor(
    private val backupDao: BackupDao,
    private val checksumProvider: ChecksumProvider
) : BackupSnapshotSource {

    override suspend fun loadSnapshot(restaurantId: String): BackupSnapshotResult {
        val snapshot = backupDao.createSnapshot(restaurantId)
        val uriToIdMap = mutableMapOf<String, String>()

        fun collectId(uri: String?) {
            if (!uri.isNullOrBlank()) {
                uriToIdMap[uri] = checksumProvider.computeAttachmentId(uri)
            }
        }

        snapshot.purchaseReceipts.forEach { collectId(it.attachmentPath) }
        snapshot.wasteEvents.forEach { collectId(it.attachmentPath) }

        val dto = BackupMapper.mapToDto(snapshot, uriToIdMap)
        val idToUriMap = uriToIdMap.map { (uri, id) -> id to AttachmentSourceUri(uri) }.toMap()

        return BackupSnapshotResult(dto, idToUriMap)
    }
}
