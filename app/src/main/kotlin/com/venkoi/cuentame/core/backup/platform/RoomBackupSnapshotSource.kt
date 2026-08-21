package com.venkoi.cuentame.core.backup.platform

import com.venkoi.cuentame.core.backup.ChecksumProvider
import com.venkoi.cuentame.core.backup.api.AttachmentSourceUri
import com.venkoi.cuentame.core.backup.api.BackupAttachmentSourceBinding
import com.venkoi.cuentame.core.backup.api.BackupSnapshotResult
import com.venkoi.cuentame.core.backup.api.BackupSnapshotSource
import com.venkoi.cuentame.core.database.dao.BackupDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBackupSnapshotSource @Inject constructor(
    private val backupDao: BackupDao,
    private val checksumProvider: ChecksumProvider
) : BackupSnapshotSource {

    override suspend fun loadSnapshot(restaurantId: String): BackupSnapshotResult {
        val snapshot = backupDao.createSnapshot(restaurantId)
        
        val uriStrings = mutableSetOf<String>()
        snapshot.purchaseReceipts.forEach { it.attachmentPath?.let { path -> if (path.isNotBlank()) uriStrings.add(path) } }
        snapshot.wasteEvents.forEach { it.attachmentPath?.let { path -> if (path.isNotBlank()) uriStrings.add(path) } }

        val uriToIdMap = uriStrings.associateWith { checksumProvider.computeAttachmentId(it) }
        
        val dto = BackupMapper.mapToDto(snapshot, uriToIdMap)
        
        val bindings = uriToIdMap.map { (uri, id) ->
            BackupAttachmentSourceBinding(id, AttachmentSourceUri(uri))
        }

        return BackupSnapshotResult(dto, bindings)
    }
}
