package com.venkoi.cuentame.core.backup

import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.*
import com.venkoi.cuentame.core.model.backup.BackupRestoreFailure
import com.venkoi.cuentame.core.backup.internal.RestoreOperationGate
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.PurchaseReceiptEntity
import com.venkoi.cuentame.core.domain.repository.BackupOperationStatus
import com.venkoi.cuentame.core.domain.repository.BackupRepository
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.test.TestSeeder
import com.venkoi.cuentame.test.TestStateManager
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
import com.venkoi.cuentame.core.backup.internal.RestoreFailureInjector
import com.venkoi.cuentame.core.backup.internal.RestoreFailureModule
import com.venkoi.cuentame.core.backup.internal.RestoreCheckpoint
import com.venkoi.cuentame.core.backup.internal.RestoreJournalDto
import com.venkoi.cuentame.core.backup.internal.RestoreJournal
import com.venkoi.cuentame.core.backup.internal.RestoreJournalReadResult
import com.venkoi.cuentame.core.backup.internal.InternalBackupRestoreStorage
import com.venkoi.cuentame.core.backup.internal.RestoreRecoveryCoordinator
import com.venkoi.cuentame.core.backup.internal.RestoreAttachmentInstaller
import com.venkoi.cuentame.core.backup.platform.BackupRestoreCoordinatorImpl
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import kotlinx.serialization.encodeToString
import org.junit.rules.Timeout
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import dagger.hilt.android.testing.BindValue

@HiltAndroidTest
@UninstallModules(RestoreFailureModule::class)
class AttachmentBackupRestoreIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val timeoutRule: Timeout = Timeout.seconds(120)

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
    lateinit var journal: RestoreJournal

    @Inject
    lateinit var storage: InternalBackupRestoreStorage

    @Inject
    lateinit var recoveryCoordinator: RestoreRecoveryCoordinator

    @Inject
    lateinit var attachmentInstaller: RestoreAttachmentInstaller

    class TestRestoreFailureInjector : RestoreFailureInjector {
        @Volatile
        var failAt: RestoreCheckpoint? = null
        
        @Volatile
        var cancelAt: RestoreCheckpoint? = null
        
        @Volatile
        var lastObserved: RestoreCheckpoint? = null

        override fun onCheckpoint(checkpoint: RestoreCheckpoint) {
            lastObserved = checkpoint
            if (checkpoint == failAt) {
                throw InjectedRestoreFailure(checkpoint)
            }
            if (checkpoint == cancelAt) {
                throw kotlinx.coroutines.CancellationException("Injected cancellation at $checkpoint")
            }
        }

        override fun injectCancellation(checkpoint: RestoreCheckpoint) {
            cancelAt = checkpoint
        }

        fun reset() {
            failAt = null
            cancelAt = null
            lastObserved = null
        }
    }

    class InjectedRestoreFailure(val checkpoint: RestoreCheckpoint) : 
        RuntimeException("Injected failure at $checkpoint")

    private val testFailureInjector = TestRestoreFailureInjector()

    @BindValue
    @JvmField
    val boundFailureInjector: RestoreFailureInjector = testFailureInjector

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            fullReset()
            restoreGate.updateRecoveryState(RestoreStartupState.Ready)
        }
    }

    @After
    fun tearDown() {
        runBlocking { fullReset() }
    }

    private suspend fun fullReset() {
        // Deterministic reset of all backup/restore artifacts
        testStateManager.resetAll()
        testFailureInjector.reset()
        
        val filesDir = storage.getFilesDir()
        File(filesDir, "attachments").deleteRecursively()
        File(filesDir, "backup_restore").deleteRecursively()
        File(filesDir, "construction").deleteRecursively()
        
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        cacheDir.listFiles()?.forEach { 
            if (it.name.startsWith("test_") || it.name.startsWith("backup_")) {
                it.delete()
            }
        }
        
        testStateManager.seedBaseline()
        // Ensure gate is Ready for normal tests
        restoreGate.updateRecoveryState(RestoreStartupState.Ready)
    }

    private suspend fun <T> boundedStep(
        name: String,
        timeoutMillis: Long = 30_000,
        block: suspend () -> T
    ): T {
        return try {
            withTimeout(timeoutMillis) {
                block()
            }
        } catch (error: TimeoutCancellationException) {
            val diagnostic = buildString {
                appendLine("Step '$name' timed out after ${timeoutMillis}ms")
                appendLine("Restore Gate: ${restoreGate.recoveryState.value}")
                appendLine("Journal: ${journal.read()}")
                appendLine("Fail At: ${testFailureInjector.failAt}")
                appendLine("Last Observed: ${testFailureInjector.lastObserved}")
                val filesDir = storage.getFilesDir()
                appendLine("Attachments Count: ${File(filesDir, "attachments").walkTopDown().filter { it.isFile }.count()}")
                appendLine("Rollback Sessions: ${File(filesDir, "backup_restore/rollback").listFiles()?.size ?: 0}")
            }
            throw AssertionError(diagnostic, error)
        }
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
            boundedStep("Reset and seed baseline") {
                fullReset()
            }
            
            val filesDir = storage.getFilesDir()
            val liveDir = File(filesDir, "attachments")
            liveDir.deleteRecursively()
            assertThat(liveDir.exists()).isFalse()
            
            // 2. Prepare a backup containing an attachment
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_for_rollback_empty.zip")
            val receiptId = "p_rollback_empty"
            val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(receiptId), "temp.pdf")
            
            boundedStep("Create source backup") {
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
            }
            
            // 3. Inject failure after attachments installed
            testFailureInjector.failAt = RestoreCheckpoint.AFTER_LIVE_ATTACHMENTS_INSTALLED
            
            // 4. Attempt restore
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val inspection = boundedStep("Inspect backup") {
                restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            }
            
            val result = boundedStep("Apply restore with injected failure") {
                restoreCoordinator.apply(docUri, inspection.archive.fingerprint) {}
            }
            
            // 5. Verify failure and rollback
            assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
            val failure = result as BackupRestoreApplyResult.Failure
            assertThat(failure.reason).isNotEqualTo(BackupRestoreFailure.RecoveryRequired)
            
            // Room restored (original empty state)
            assertThat(database.purchaseDao().getReceiptById(receiptId)).isNull()
            
            // Filesystem rolled back (empty tree)
            val restoredLiveFile = File(filesDir, relativePath)
            assertThat(restoredLiveFile.exists()).isFalse()
            assertThat(liveDir.walkTopDown().filter { it.isFile }.count()).isEqualTo(0)
            
            // Session and journal cleaned
            assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
            val rollbackRoot = File(storage.getFilesDir(), "backup_restore/rollback")
            val remainingSessions = rollbackRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
            assertThat(remainingSessions).isEmpty()

            // Gate remains usable
            assertThat(restoreGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)
            
            backupFile.delete()
        }
    }

    @Test
    fun synchronous_rollback_after_failure_with_originally_nonempty_tree() {
        runBlocking {
            // 1. Seed original state with attachment
            boundedStep("Reset and seed baseline") {
                fullReset()
            }
            
            val filesDir = storage.getFilesDir()
            
            val originalId = "p_original_rollback"
            val originalPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(originalId), "original.pdf")
            val originalFile = File(filesDir, originalPath)
            val originalBytes = boundedStep("Prepare original data") {
                originalFile.parentFile?.mkdirs()
                createMinimalPdf(originalFile)
                val bytes = originalFile.readBytes()
                
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
                bytes
            }

            // 2. Prepare incoming backup with DIFFERENT data
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_incoming.zip")
            boundedStep("Prepare incoming backup") {
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
            testFailureInjector.failAt = RestoreCheckpoint.AFTER_LIVE_ATTACHMENTS_INSTALLED
            
            // 4. Attempt restore
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val inspection = boundedStep("Inspect backup") {
                restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            }
            val result = boundedStep("Apply restore with injected failure") {
                restoreCoordinator.apply(docUri, inspection.archive.fingerprint) {}
            }
            
            // 5. Verify rollback to original
            assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
            val failure = result as BackupRestoreApplyResult.Failure
            assertThat(failure.reason).isNotEqualTo(BackupRestoreFailure.RecoveryRequired)
            
            val restoredReceipt = database.purchaseDao().getReceiptById(originalId)
            assertThat(restoredReceipt).isNotNull()
            assertThat(restoredReceipt?.attachmentPath).isEqualTo(originalPath)
            assertThat(restoredReceipt?.attachmentDisplayName).isEqualTo("Original.pdf")
            
            val restoredFile = File(filesDir, originalPath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
            
            // Verify through Store API (Task 10)
            assertThat(documentStore.inspect(originalPath)).isNotNull()
            documentStore.open(originalPath).use {
                assertThat(it.readBytes()).isEqualTo(originalBytes)
            }

            // Incoming file is gone
            val incomingId = "p_incoming"
            val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "incoming.pdf")
            assertThat(File(filesDir, incomingPath).exists()).isFalse()
            
            // Metadata clean
            assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
            
            backupFile.delete()
        }
    }

    @Test
    fun cancellation_after_live_attachments_installed_rolls_back_before_rethrow() {
        runBlocking {
            // 1. Seed original state with attachment
            fullReset()
            val filesDir = storage.getFilesDir()
            val originalId = "p_cancel_original"
            val originalPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(originalId), "orig.pdf")
            val originalFile = File(filesDir, originalPath)
            originalFile.parentFile?.mkdirs()
            createMinimalPdf(originalFile)
            val originalBytes = originalFile.readBytes()
            
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = originalId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "CANCEL-ORIG",
                    purchaseDate = System.currentTimeMillis(),
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = originalPath,
                    attachmentDisplayName = "Orig.pdf",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    postedAt = null,
                    voidedAt = null
                )
            )

            // 2. Prepare incoming backup
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_cancel.zip")
            val incomingId = "p_inc_cancel"
            val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "inc.pdf")
            val incomingFile = File(filesDir, incomingPath)
            incomingFile.parentFile?.mkdirs()
            createMinimalPdf(incomingFile)
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = incomingId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INC",
                    purchaseDate = System.currentTimeMillis(),
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = incomingPath,
                    attachmentDisplayName = "Inc.pdf",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    postedAt = null,
                    voidedAt = null
                )
            )
            backupRepository.createBackup(backupFile.absolutePath).toList()
            database.purchaseDao().deleteDraftReceipt(incomingId)
            incomingFile.delete()

            // 3. Inject cancellation
            testFailureInjector.injectCancellation(RestoreCheckpoint.AFTER_LIVE_ATTACHMENTS_INSTALLED)

            // 4. Call apply() and expect cancellation
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val inspection = restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            
            try {
                restoreCoordinator.apply(docUri, inspection.archive.fingerprint) {}
                throw AssertionError("Should have been cancelled")
            } catch (e: kotlinx.coroutines.CancellationException) {
                assertThat(e.message).contains("Injected cancellation")
            }

            // 5. Verify postconditions
            // Original data restored
            val restoredReceipt = database.purchaseDao().getReceiptById(originalId)
            assertThat(restoredReceipt).isNotNull()
            assertThat(restoredReceipt?.attachmentDisplayName).isEqualTo("Orig.pdf")
            
            val restoredFile = File(filesDir, originalPath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)

            // Incoming absent
            assertThat(File(filesDir, incomingPath).exists()).isFalse()

            // Artifacts clean
            assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
            val rollbackRoot = File(storage.getFilesDir(), "backup_restore/rollback")
            val remainingSessions = rollbackRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
            assertThat(remainingSessions).isEmpty()
            
            // Gate remains operational
            assertThat(restoreGate.recoveryState.value).isEqualTo(RestoreStartupState.Ready)

            backupFile.delete()
        }
    }

    @Test
    fun startup_recovery_after_interrupted_restore_with_originally_nonempty_tree() {
        runBlocking {
            // 1. Prepare original state
            boundedStep("Reset and seed baseline") {
                fullReset()
            }
            val filesDir = storage.getFilesDir()
            
            val originalId = "p_recovery_original"
            val originalPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(originalId), "orig.pdf")
            val originalFile = File(filesDir, originalPath)
            val originalBytes = boundedStep("Prepare original data") {
                originalFile.parentFile?.mkdirs()
                createMinimalPdf(originalFile)
                val bytes = originalFile.readBytes()
                bytes
            }
            
            // 2. Capture rollback and write a journal manually to simulate interruption
            val sessionId = "recovery-session-test"
            boundedStep("Manual journal and rollback setup") {
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
            }
            
            // 3. Mutate live tree (simulate interruption after some mutation)
            val incomingId = "p_recovery_incoming"
            val incomingPath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(incomingId), "inc.pdf")
            boundedStep("Simulate interruption mutation") {
                val incomingFile = File(filesDir, incomingPath)
                incomingFile.parentFile?.mkdirs()
                createMinimalPdf(incomingFile)
            }
            
            // 4. Invoke recovery
            val recoveryResult = boundedStep("Retry recovery") {
                restoreCoordinator.retryRecovery()
            }
            assertThat(recoveryResult).isInstanceOf(RestoreRecoveryResult.Recovered::class.java)
            
            // 5. Verify original state restored
            val restoredFile = File(filesDir, originalPath)
            assertThat(restoredFile.exists()).isTrue()
            assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
            
            assertThat(File(filesDir, incomingPath).exists()).isFalse()
            
            // Journal and session cleaned
            assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
            
            // Gate state updated
            assertThat(restoreGate.recoveryState.value).isInstanceOf(RestoreStartupState.Recovered::class.java)
        }
    }

    @Inject
    lateinit var databaseApplier: com.venkoi.cuentame.core.backup.internal.RestoreDatabaseApplier

    @Inject
    lateinit var preferencesApplier: com.venkoi.cuentame.core.backup.internal.RestorePreferencesApplier

    @Inject
    lateinit var codecs: BackupJsonCodecs

    @Test
    fun complete_backup_restore_roundtrip_with_valid_pdf() {
        runBlocking {
            boundedStep("Reset and seed baseline") {
                fullReset()
            }
            val now = Instant.now().toEpochMilli()
            val receiptId = "p_roundtrip_valid"
            val displayName = "test_attachment_invoice.pdf"
            val storageFilename = "test_attachment_invoice.pdf"
            val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(PurchaseReceiptId(receiptId), storageFilename)
            val filesDir = storage.getFilesDir()
            val targetFile = File(filesDir, relativePath)
            
            val originalBytes = boundedStep("Create source data and backup") {
                targetFile.parentFile?.mkdirs()
                createMinimalPdf(targetFile)
                val bytes = targetFile.readBytes()
                
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
                bytes
            }
            
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test_valid_pdf_roundtrip.zip")

            boundedStep("Clear data before restore") {
                testStateManager.resetAll()
                testStateManager.seedBaseline()
                assertThat(database.purchaseDao().getReceiptById(receiptId)).isNull()
                assertThat(targetFile.exists()).isFalse()
            }
            
            // 4. Restore
            val docUri = BackupDocumentUri("file://${backupFile.absolutePath}")
            val ready = boundedStep("Inspect backup") {
                restoreCoordinator.inspect(docUri) as BackupArchiveInspectionResult.Ready
            }
            
            val restoreResult = boundedStep("Apply restore") {
                restoreCoordinator.apply(docUri, ready.archive.fingerprint) {}
            }
            assertThat(restoreResult).isEqualTo(BackupRestoreApplyResult.Success)
            
            // 5. Verify restored
            boundedStep("Verify restored state") {
                val restoredReceipt = database.purchaseDao().getReceiptById(receiptId)
                assertThat(restoredReceipt).isNotNull()
                assertThat(restoredReceipt?.attachmentPath).isEqualTo(relativePath)
                assertThat(restoredReceipt?.attachmentDisplayName).isEqualTo(displayName)
                
                val restoredFile = File(filesDir, relativePath)
                assertThat(restoredFile.exists()).isTrue()
                assertThat(restoredFile.readBytes()).isEqualTo(originalBytes)
                
                // Verify PurchaseDocumentStore.inspect() succeeds
                val metadata = documentStore.inspect(relativePath)
                assertThat(metadata).isNotNull()
            }
            
            backupFile.delete()
        }
    }
}
