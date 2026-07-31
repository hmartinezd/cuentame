package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.BackupCleanupCoordinator
import com.miara.cuentame.core.backup.platform.BackupStorageErrorClassifier
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupRepository @Inject constructor(
    private val snapshotSource: BackupSnapshotSource,
    private val documentStore: BackupDocumentStore,
    private val planner: BackupCreationPlanner,
    private val errorClassifier: BackupStorageErrorClassifier,
    private val restaurantRepository: RestaurantRepository,
    private val cleanupCoordinator: BackupCleanupCoordinator,
    private val archiveWriter: BackupArchiveWriter,
    private val archiveValidator: BackupArchiveValidator
) : BackupRepository {

    override fun createBackup(destinationUri: String): Flow<BackupOperationStatus> = flow {
        emit(BackupOperationStatus.Creating)
        val docUri = BackupDocumentUri(destinationUri)
        try {
            val restaurant = restaurantRepository.getRestaurant()
                ?: throw BackupCreationException(BackupResult.Error.RestaurantUnavailable)

            val snapshotResult = try {
                snapshotSource.loadSnapshot(restaurant.id.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw BackupCreationException(BackupResult.Error.DatabaseSnapshotFailure) }

            val planningResult = planner.createPlan(restaurant, snapshotResult)
            if (planningResult is BackupPlanningResult.Failure) {
                throw BackupCreationException(mapPlanningFailure(planningResult.reason))
            }

            val plan = (planningResult as BackupPlanningResult.Success).plan

            documentStore.openForWrite(docUri).use { os ->
                BufferedOutputStream(os).use { bos ->
                    val writeResult = archiveWriter.write(bos, plan)
                    if (writeResult is BackupArchiveWriteResult.Failure) {
                        throw BackupCreationException(mapWriterFailure(writeResult))
                    }
                }
            }

            emit(BackupOperationStatus.Validating)
            val validation = validateBackup(destinationUri)
            if (validation is BackupValidationResult.Valid) {
                emit(BackupOperationStatus.Success(validation.manifest))
            } else {
                val invalid = validation as BackupValidationResult.Invalid
                cleanupSafely(docUri)
                emit(BackupOperationStatus.Error(BackupResult.Error.ArchiveValidationFailure(invalid.code, invalid.diagnostic)))
            }
        } catch (e: CancellationException) {
            cleanupSafely(docUri)
            throw e
        } catch (e: BackupCreationException) {
            cleanupSafely(docUri)
            emit(BackupOperationStatus.Error(e.error))
        } catch (e: Exception) {
            cleanupSafely(docUri)
            emit(BackupOperationStatus.Error(mapGeneralException(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun mapPlanningFailure(reason: BackupPlanningFailure): BackupResult.Error {
        return when (reason) {
            BackupPlanningFailure.RestaurantDisappeared -> BackupResult.Error.RestaurantUnavailable
            BackupPlanningFailure.LocaleReconciliationFailed -> BackupResult.Error.LocaleConsistencyFailure
            BackupPlanningFailure.PreferencesReadFailed -> BackupResult.Error.PreferencesReadFailure
            BackupPlanningFailure.UnsupportedRestaurantLocale -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.UnsupportedPreferencesLocale -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.PreferencesLocaleMismatch -> BackupResult.Error.LocaleConsistencyFailure
            BackupPlanningFailure.InvalidPreferences -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.InvalidSnapshot -> BackupResult.Error.DatabaseSnapshotFailure
            BackupPlanningFailure.MissingAttachmentSource -> BackupResult.Error.MissingAttachment
            BackupPlanningFailure.ConflictingAttachmentSource -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.ExtraAttachmentSource -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.UnreadableAttachment -> BackupResult.Error.UnreadableAttachment
            BackupPlanningFailure.InvalidAttachmentMetadata -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.InvalidAttachmentId -> BackupResult.Error.AttachmentPreflightFailure
            BackupPlanningFailure.AttachmentLimitExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.EntryNameLimitExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.TotalSizeLimitExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.ArchiveEntryCountExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.JsonLimitExceeded -> BackupResult.Error.LimitExceeded
            BackupPlanningFailure.SerializationFailed -> BackupResult.Error.SerializationFailure
            BackupPlanningFailure.UnexpectedPlanningFailure -> BackupResult.Error.UnexpectedInternalFailure
            BackupPlanningFailure.UnsupportedDatabaseSchema -> BackupResult.Error.UnsupportedPersistentData
            BackupPlanningFailure.AttachmentsNotSupported -> BackupResult.Error.AttachmentsNotSupported
        }
    }

    private fun mapWriterFailure(failure: BackupArchiveWriteResult.Failure): BackupResult.Error {
        return when (failure) {
            BackupArchiveWriteResult.Failure.AttachmentUnreadable -> BackupResult.Error.UnreadableAttachment
            BackupArchiveWriteResult.Failure.AttachmentChanged -> BackupResult.Error.AttachmentPreflightFailure
            BackupArchiveWriteResult.Failure.LimitExceeded -> BackupResult.Error.LimitExceeded
            BackupArchiveWriteResult.Failure.InvalidPlan -> BackupResult.Error.UnexpectedInternalFailure
            BackupArchiveWriteResult.Failure.ChecksumInconsistency -> BackupResult.Error.ChecksumFailure
            is BackupArchiveWriteResult.Failure.IoError -> mapGeneralException(failure.cause)
        }
    }

    private fun mapGeneralException(e: Exception): BackupResult.Error {
        val failure = errorClassifier.classify(e)
        return when (failure) {
            BackupStorageFailure.InsufficientSpace -> BackupResult.Error.InsufficientStorage
            BackupStorageFailure.PermissionDenied -> BackupResult.Error.PermissionDenied
            BackupStorageFailure.DestinationUnavailable -> BackupResult.Error.DestinationUnavailable
            BackupStorageFailure.GenericIo -> BackupResult.Error.SystemIOFailure
        }
    }

    private suspend fun cleanupSafely(uri: BackupDocumentUri) {
        withContext(NonCancellable) {
            cleanupCoordinator.cleanup(uri)
        }
    }

    override suspend fun validateBackup(sourceUri: String): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            documentStore.openForRead(BackupDocumentUri(sourceUri)).use { inputStream ->
                archiveValidator.validate(inputStream)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupValidationResult.Invalid(BackupValidationCode.SYSTEM_IO_ERROR)
        }
    }

    private class BackupCreationException(val error: BackupResult.Error) : Exception()
}
