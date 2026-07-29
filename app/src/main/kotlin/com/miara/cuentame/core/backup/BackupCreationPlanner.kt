package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.AttachmentSourceUri
import com.miara.cuentame.core.backup.api.BackupSnapshotResult
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class BackupPlan(
    val snapshotDto: com.miara.cuentame.core.backup.model.BackupSnapshotDto,
    val preferencesDto: BackupPreferencesDto,
    val baseManifest: BackupManifest,
    val attachments: List<PendingAttachmentPlan>
)

data class PendingAttachmentPlan(
    val sourceUri: AttachmentSourceUri,
    val attachmentId: String,
    val references: List<BackupAttachmentReference>
)

@Singleton
class BackupCreationPlanner @Inject constructor(
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider
) {

    fun createPlan(
        restaurant: Restaurant,
        snapshotResult: BackupSnapshotResult,
        preferencesDto: BackupPreferencesDto
    ): Result<BackupPlan> {
        val snapshotDto = snapshotResult.dto
        
        // 1. Collect all references per attachment ID
        val attachmentRefs = mutableMapOf<String, MutableList<BackupAttachmentReference>>()
        
        snapshotDto.purchaseReceipts.forEach { receipt ->
            receipt.attachmentId?.let { id ->
                attachmentRefs.getOrPut(id) { mutableListOf() }
                    .add(BackupAttachmentReference("PURCHASE_RECEIPT", receipt.id))
            }
        }
        
        snapshotDto.wasteEvents.forEach { event ->
            event.attachmentId?.let { id ->
                attachmentRefs.getOrPut(id) { mutableListOf() }
                    .add(BackupAttachmentReference("WASTE_EVENT", event.id))
            }
        }

        // 2. Map to PendingAttachmentPlan
        val attachmentPlans = attachmentRefs.map { (id, refs) ->
            val uri = snapshotResult.attachmentUris[id] 
                ?: return Result.failure(Exception("Missing source URI for attachment $id"))
            PendingAttachmentPlan(uri, id, refs.sortedWith(compareBy({ it.recordType }, { it.recordId })))
        }.sortedBy { it.attachmentId }

        if (attachmentPlans.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
            return Result.failure(Exception("Exceeded maximum attachment limit"))
        }

        // 3. Create Base Manifest
        val baseManifest = BackupManifest(
            backupFormatVersion = BackupLimits.BACKUP_FORMAT_VERSION,
            createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(timeProvider.now()),
            applicationId = appVersionProvider.applicationId,
            appVersionName = appVersionProvider.versionName,
            appVersionCode = appVersionProvider.versionCode,
            databaseSchemaVersion = appVersionProvider.databaseSchemaVersion,
            restaurantId = restaurant.id.value,
            restaurantName = restaurant.name,
            localeTag = restaurant.localeTag,
            currencyCode = restaurant.currencyCode,
            tableMetadata = emptyMap(), // To be filled during ZIP creation
            attachments = emptyList(), // To be filled during ZIP creation
            includedSections = listOf("data", "preferences", "attachments").sorted(),
            checksumAlgorithm = "SHA-256"
        )

        return Result.success(BackupPlan(
            snapshotDto = snapshotDto,
            preferencesDto = preferencesDto,
            baseManifest = baseManifest,
            attachments = attachmentPlans
        ))
    }
}
