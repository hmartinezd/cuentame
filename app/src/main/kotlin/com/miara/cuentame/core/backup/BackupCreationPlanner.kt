package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCreationPlanner @Inject constructor(
    private val localeReconciler: AppLocaleReconciler,
    private val preferencesSource: BackupPreferencesSource,
    private val attachmentSource: BackupAttachmentSource,
    private val timeProvider: TimeProvider,
    private val appVersionProvider: AppVersionProvider
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    suspend fun createPlan(
        restaurant: Restaurant,
        snapshotResult: BackupSnapshotResult
    ): BackupPlanningResult {
        try {
            // 1. Reconcile locale
            val reconciliation = localeReconciler.reconcile()
            if (reconciliation is LocaleReconciliationResult.Failure) {
                return failure(BackupPlanningFailure.LocaleReconciliationFailed)
            }

            // 2. Reload preferences after reconciliation
            val preferencesDto = preferencesSource.loadPreferences()

            // 3. Verify locales
            if (SupportedAppLocale.fromLanguageTag(restaurant.localeTag) == null) {
                return failure(BackupPlanningFailure.UnsupportedRestaurantLocale)
            }
            if (SupportedAppLocale.fromLanguageTag(preferencesDto.appLocaleTag) == null) {
                return failure(BackupPlanningFailure.UnsupportedPreferencesLocale)
            }
            if (restaurant.localeTag != preferencesDto.appLocaleTag) {
                return failure(BackupPlanningFailure.PreferencesLocaleMismatch)
            }

            // 4. Validate theme
            try {
                ThemeMode.valueOf(preferencesDto.themeMode)
            } catch (e: Exception) {
                return failure(BackupPlanningFailure.InvalidPreferences)
            }

            // 5. Create base manifest
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
                tableMetadata = emptyMap(),
                attachments = emptyList(),
                includedSections = listOf("data", "preferences", "attachments").sorted(),
                checksumAlgorithm = "SHA-256"
            )

            // 6. Validate snapshot integrity
            val snapshotDto = snapshotResult.dto
            BackupSnapshotIntegrityValidator.validate(snapshotDto, baseManifest).getOrElse {
                return failure(BackupPlanningFailure.InvalidSnapshot)
            }

            // 7. Collect and inspect attachments
            val plannedAttachments = mutableListOf<PlannedBackupAttachment>()
            val attachmentRefs = mutableMapOf<String, MutableList<BackupAttachmentReference>>()

            snapshotDto.purchaseReceipts.forEach { r ->
                r.attachmentId?.let { id ->
                    attachmentRefs.getOrPut(id) { mutableListOf() }
                        .add(BackupAttachmentReference("PURCHASE_RECEIPT", r.id))
                }
            }
            snapshotDto.wasteEvents.forEach { w ->
                w.attachmentId?.let { id ->
                    attachmentRefs.getOrPut(id) { mutableListOf() }
                        .add(BackupAttachmentReference("WASTE_EVENT", w.id))
                }
            }

            if (attachmentRefs.size > BackupLimits.MAX_ATTACHMENT_COUNT) {
                return failure(BackupPlanningFailure.AttachmentLimitExceeded)
            }

            for ((id, refs) in attachmentRefs) {
                val uri = snapshotResult.attachmentUris[id] ?: return failure(BackupPlanningFailure.MissingAttachmentSource)
                
                val metadata = try {
                    attachmentSource.inspect(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return failure(BackupPlanningFailure.UnreadableAttachment)
                }

                val sanitizedDisplayName = AttachmentFilenameSanitizer.sanitize(metadata.displayName)
                if (sanitizedDisplayName.isBlank()) {
                    return failure(BackupPlanningFailure.InvalidAttachmentMetadata)
                }

                val archivePath = "attachments/$id/$sanitizedDisplayName"
                if (!ArchiveEntryValidator.isSafe(archivePath)) {
                    return failure(BackupPlanningFailure.InvalidAttachmentMetadata)
                }

                // Verify stream availability
                try {
                    attachmentSource.open(uri).close()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return failure(BackupPlanningFailure.UnreadableAttachment)
                }

                plannedAttachments.add(
                    PlannedBackupAttachment(
                        sourceUri = uri,
                        attachmentId = id,
                        archivePath = archivePath,
                        displayName = sanitizedDisplayName,
                        mimeType = metadata.mimeType,
                        references = refs.sortedWith(compareBy({ it.recordType }, { it.recordId }))
                    )
                )
            }

            // 8. Serialize and check limits
            val snapshotJson = json.encodeToString(snapshotDto).toByteArray(Charsets.UTF_8)
            if (snapshotJson.size > BackupLimits.MAX_DATABASE_JSON_BYTES) {
                return failure(BackupPlanningFailure.JsonLimitExceeded)
            }

            val preferencesJson = json.encodeToString(preferencesDto).toByteArray(Charsets.UTF_8)
            if (preferencesJson.size > BackupLimits.MAX_SETTINGS_JSON_BYTES) {
                return failure(BackupPlanningFailure.JsonLimitExceeded)
            }

            return BackupPlanningResult.Success(
                BackupPlan(
                    snapshotDto = snapshotDto,
                    snapshotJson = snapshotJson,
                    preferencesDto = preferencesDto,
                    preferencesJson = preferencesJson,
                    baseManifest = baseManifest,
                    attachments = plannedAttachments.sortedBy { it.attachmentId }
                )
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(BackupPlanningFailure.InvalidSnapshot) // Generic fallback for planning
        }
    }

    private fun failure(reason: BackupPlanningFailure): BackupPlanningResult =
        BackupPlanningResult.Failure(reason)
}
