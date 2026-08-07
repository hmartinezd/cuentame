package com.miara.cuentame.core.backup

import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import com.miara.cuentame.core.backup.internal.RestoreFailureInjector
import com.miara.cuentame.core.backup.internal.RestoreFailureModule
import com.miara.cuentame.core.backup.internal.RestoreCheckpoint
import com.miara.cuentame.core.backup.internal.RestoreJournalDto
import com.miara.cuentame.core.backup.internal.RestoreJournal
import com.miara.cuentame.core.backup.internal.RestoreJournalReadResult
import com.miara.cuentame.core.backup.internal.InternalBackupRestoreStorage
import com.miara.cuentame.core.backup.internal.RestoreRecoveryCoordinator
import com.miara.cuentame.core.backup.internal.RestoreAttachmentInstaller
import com.miara.cuentame.core.backup.platform.BackupRestoreCoordinatorImpl
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import kotlinx.serialization.encodeToString

@HiltAndroidTest
@UninstallModules(RestoreFailureModule::class)
class AttachmentBackupRestoreIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var backupRepository: BackupRepository

    @Inject
    lateinit var restoreCoordinator: BackupRestoreCoordinator

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    @Inject
    lateinit var documentStore: PurchaseDocumentStore

    @Inject
    lateinit var failureInjector: TestFailureInjector

    @Inject
    lateinit var journal: RestoreJournal

    @Inject
    lateinit var storage: InternalBackupRestoreStorage

    @Inject
    lateinit var recoveryCoordinator: RestoreRecoveryCoordinator

    class TestFailureInjector : RestoreFailureInjector {
        var failAt: RestoreCheckpoint? = null
        override fun onCheckpoint(checkpoint: RestoreCheckpoint) {
            if (checkpoint == failAt) {
                throw RuntimeException("Injected failure at $checkpoint")
            }
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            restoreGate.updateRecoveryState(RestoreStartupState.Ready)
        }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    private fun createMinimalPdf(file: File) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(100, 100, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawText("Test PDF content", 10f, 10f, android.graphics.Paint())
        pdfDocument.finishPage(page)
        file.outputStream().use { 
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }

    @Test
    fun synchronous_rollback_after_failure_with_originally_empty_tree() {
        runBlocking {
            // 1. Start with no attachments
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            val liveDir = File(filesDir, "attachments")
            liveDir.deleteRecursively()
            assertThat(liveDir.exists()).isFalse()
            
            // 2. Prepare a backup containing an attachment
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_for_rollback_empty.zip")
            val receiptId = "p_rollback_empty"
            val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(receiptId), "temp.pdf")
            val file = File(filesDir, relativePath)
            file.parentFile?.mkdirs()
            createMinimalPdf(file)
            
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "TMP",
                    purchaseDate = System.currentTimeMillis(),
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = relativePath,
                    attachmentDisplayName = "Temp.pdf",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    postedAt = null,
                    voidedAt = null
                )
            )
            
            val statuses = backupRepository.createBackup(backupFile.absolutePath).toList()
            assertThat(statuses.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
            
            // Clear again to simulate incoming restore
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            liveDir.deleteRecursively()
            
            // 3. Inject failure after attachments installed
            failureInjector.failAt = RestoreCheckpoint.AFTER_LIVE_ATTACHMENTS_INSTALLED
            
            // 4. Attempt restore
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val inspection = restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            
            val result = restoreCoordinator.apply(docUri, inspection.archive.fingerprint) {}
            
            // 5. Verify failure and rollback
            assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
            
            // Room restored (original empty state)
            assertThat(database.purchaseDao().getReceiptById(receiptId)).isNull()
            
            // Filesystem rolled back (empty tree)
            val restoredLiveFile = File(filesDir, relativePath)
            assertThat(restoredLiveFile.exists()).isFalse()
            assertThat(liveDir.walkTopDown().filter { it.isFile }.count()).isEqualTo(0)
            
            // Gate remains usable
            assertThat(restoreGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)
            
            backupFile.delete()
        }
    }

    @Test
    fun synchronous_rollback_after_failure_with_originally_nonempty_tree() {
        runBlocking {
            // 1. Seed original state with attachment
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            
            val originalId = "p_original_rollback"
            val originalPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(originalId), "original.pdf")
            val originalFile = File(filesDir, originalPath)
            originalFile.parentFile?.mkdirs()
            createMinimalPdf(originalFile)
            val originalBytes = originalFile.readBytes()
            
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = originalId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "ORIGINAL",
                    purchaseDate = System.currentTimeMillis(),
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = originalPath,
                    attachmentDisplayName = "Original.pdf",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    postedAt = null,
                    voidedAt = null
                )
            )

            // 2. Prepare incoming backup with DIFFERENT data
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_incoming.zip")
            run {
                val incomingId = "p_incoming"
                val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "incoming.pdf")
                val incomingFile = File(filesDir, incomingPath)
                incomingFile.parentFile?.mkdirs()
                createMinimalPdf(incomingFile)
                
                // Temporarily update DB to create backup
                database.purchaseDao().insertReceipt(
                    PurchaseReceiptEntity(
                        id = incomingId,
                        restaurantId = TestSeeder.RESTAURANT_ID,
                        supplierId = null,
                        invoiceNumber = "INCOMING",
                        purchaseDate = System.currentTimeMillis(),
                        status = DocumentStatus.DRAFT.name,
                        notes = null,
                        attachmentPath = incomingPath,
                        attachmentDisplayName = "Incoming.pdf",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        postedAt = null,
                        voidedAt = null
                    )
                )
                backupRepository.createBackup(backupFile.absolutePath).toList()
                
                // Revert DB to original state for the test
                database.purchaseDao().deleteDraftReceipt(incomingId)
                incomingFile.delete()
            }
            
            // 3. Inject failure
            failureInjector.failAt = RestoreCheckpoint.AFTER_LIVE_ATTACHMENTS_INSTALLED
            
            // 4. Attempt restore
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val inspection = restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            val result = restoreCoordinator.apply(docUri, inspection.archive.fingerprint) {}
            
            // 5. Verify rollback to original
            assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
            
            val restoredReceipt = database.purchaseDao().getReceiptById(originalId)
            assertThat(restoredReceipt).isNotNull()
            assertThat(restoredReceipt?.attachmentPath).isEqualTo(originalPath)
            
            val restoredFile = File(filesDir, originalPath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
            
            // Incoming file is gone
            val incomingId = "p_incoming"
            val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "incoming.pdf")
            assertThat(File(filesDir, incomingPath).exists()).isFalse()
            
            backupFile.delete()
        }
    }

    @Test
    fun startup_recovery_after_interrupted_restore_with_originally_nonempty_tree() {
        runBlocking {
            // 1. Prepare original state
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            
            val originalId = "p_recovery_original"
            val originalPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(originalId), "orig.pdf")
            val originalFile = File(filesDir, originalPath)
            originalFile.parentFile?.mkdirs()
            createMinimalPdf(originalFile)
            val originalBytes = originalFile.readBytes()
            
            // 2. Capture rollback and write a journal manually to simulate interruption
            val sessionId = "recovery-session-test"
            val inventory = attachmentInstaller.captureRollback(sessionId)
            val rollback = databaseApplier.captureRollbackSnapshot().copy(attachmentInventory = inventory)
            
            val journalDto = RestoreJournalDto(
                sessionId = sessionId,
                phase = RestorePhase.MUTATION_STARTED,
                expectedArchiveFingerprint = "dummy",
                previousPreferences = preferencesApplier.captureRollback(),
                attachmentInventory = inventory,
                startedAt = System.currentTimeMillis()
            )
            
            journal.write(journalDto)
            storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString(rollback))
            
            // 3. Mutate live tree (simulate interruption after some mutation)
            val incomingId = "p_recovery_incoming"
            val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "inc.pdf")
            val incomingFile = File(filesDir, incomingPath)
            incomingFile.parentFile?.mkdirs()
            createMinimalPdf(incomingFile)
            
            // 4. Invoke recovery
            val recoveryResult = recoveryCoordinator.retryRecovery()
            assertThat(recoveryResult).isInstanceOf(RestoreRecoveryResult.Recovered::class.java)
            
            // 5. Verify original state restored
            val restoredFile = File(filesDir, originalPath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
            
            assertThat(incomingFile.exists()).isFalse()
            
            // Journal and session cleaned
            assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
            
            // Gate state updated
            assertThat(restoreGate.recoveryState.value).isInstanceOf(RestoreStartupState.Recovered::class.java)
        }
    }

    private val attachmentInstaller: RestoreAttachmentInstaller
        get() = (restoreCoordinator as BackupRestoreCoordinatorImpl).let { 
             val field = BackupRestoreCoordinatorImpl::class.java.getDeclaredField("attachmentInstaller")
             field.isAccessible = true
             field.get(it) as RestoreAttachmentInstaller
        }

    @Inject
    lateinit var databaseApplier: com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier

    @Inject
    lateinit var preferencesApplier: com.miara.cuentame.core.backup.internal.RestorePreferencesApplier

    @Inject
    lateinit var codecs: BackupJsonCodecs

    @Test
    fun complete_backup_restore_roundtrip_with_valid_pdf() {
        runBlocking {
            val now = Instant.now().toEpochMilli()
            val receiptId = "p_roundtrip_valid"
            val displayName = "Original Invoice.pdf"
            val storageFilename = "test_attachment_invoice_${System.currentTimeMillis()}.pdf"
            val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(receiptId), storageFilename)
            val targetFile = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, relativePath)
            targetFile.parentFile?.mkdirs()
            
            createMinimalPdf(targetFile)
            val originalBytes = targetFile.readBytes()
            
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INV-RT-VALID",
                    purchaseDate = now,
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = relativePath,
                    attachmentDisplayName = displayName,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            
            // 2. Create backup
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test_valid_pdf_roundtrip.zip")
            val backupUri = backupFile.absolutePath
            val backupStatuses = backupRepository.createBackup(backupUri).toList()
            assertThat(backupStatuses.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
            
            // 3. Clear data
            testStateManager.resetAll()
            assertThat(database.purchaseDao().getReceiptById(receiptId)).isNull()
            assertThat(targetFile.exists()).isFalse()
            
            // 4. Restore
            val docUri = BackupDocumentUri("file://$backupUri")
            val inspection = restoreCoordinator.inspect(docUri)
            assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
            val ready = inspection as BackupArchiveInspectionResult.Ready
            
            val restoreResult = restoreCoordinator.apply(docUri, ready.archive.fingerprint) {}
            assertThat(restoreResult).isEqualTo(BackupRestoreApplyResult.Success)
            
            // 5. Verify restored
            val restoredReceipt = database.purchaseDao().getReceiptById(receiptId)
            assertThat(restoredReceipt).isNotNull()
            assertThat(restoredReceipt?.attachmentPath).isEqualTo(relativePath)
            assertThat(restoredReceipt?.attachmentDisplayName).isEqualTo(displayName)
            
            val restoredFile = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, relativePath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
            
            // Verify PurchaseDocumentStore.inspect() succeeds
            val metadata = documentStore.inspect(relativePath)
            assertThat(metadata).isNotNull()
            
            backupFile.delete()
        }
    }
}
