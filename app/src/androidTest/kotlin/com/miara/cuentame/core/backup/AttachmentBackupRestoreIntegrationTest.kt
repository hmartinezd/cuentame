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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
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
    fun complete_backup_restore_roundtrip_with_valid_pdf() {
        runBlocking {
            val now = Instant.now().toEpochMilli()
            val receiptId = "p_roundtrip_valid"
            val displayName = "Original Invoice.pdf"
            val storageFilename = "test_attachment_invoice_${System.currentTimeMillis()}.pdf"
            val relativePath = "attachments/purchases/$receiptId/$storageFilename"
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
            
            // 6. Verify second backup succeeds after restore
            val backupFile2 = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test_roundtrip_2.zip")
            val backupStatuses2 = backupRepository.createBackup(backupFile2.absolutePath).toList()
            assertThat(backupStatuses2.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
            
            // Cleanup
            backupFile.delete()
            backupFile2.delete()
        }
    }

    @Test
    fun rollback_clears_incoming_files_when_original_was_empty() {
        runBlocking {
            // 1. Start with no attachments
            testStateManager.resetAll()
            testStateManager.seedBaseline()
            val liveDir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "attachments")
            liveDir.deleteRecursively()
            assertThat(liveDir.exists()).isFalse()
            
            // 2. Prepare a backup containing an attachment
            val backupFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup_with_att_rollback.zip")
            run {
                val receiptId = "p_temp_rollback"
                val relativePath = "attachments/purchases/$receiptId/temp.pdf"
                val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, relativePath)
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
                
                testStateManager.resetAll()
                testStateManager.seedBaseline()
                liveDir.deleteRecursively()
            }
            
            // Cleanup zip
            backupFile.delete()
        }
    }
}
