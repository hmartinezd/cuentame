package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupArchiveInspectionResult
import com.miara.cuentame.core.backup.api.BackupRestoreApplyResult
import com.miara.cuentame.core.backup.api.BackupRestoreCoordinator
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
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

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class PurchaseDocumentUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var backupRepository: BackupRepository

    @Inject
    lateinit var restoreCoordinator: BackupRestoreCoordinator

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
        }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    private fun waitForHomeScreen() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun documentSection_showsSaveMessageBeforeFirstSave() {
        waitForHomeScreen()
        composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("add_purchase_fab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_purchase_fab", useUnmergedTree = true).performClick()
        
        composeTestRule.onNodeWithTag("purchase_document_section").assertIsDisplayed()
        val saveMsg = composeTestRule.activity.getString(R.string.purchase_save_header_first)
        composeTestRule.onNodeWithText(saveMsg).assertIsDisplayed()
    }

    @Test
    fun documentSection_showsImportAfterFirstSave() {
        waitForHomeScreen()
        composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("add_purchase_fab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_purchase_fab", useUnmergedTree = true).performClick()
        
        composeTestRule.onNodeWithTag("purchase_header_save").performClick()
        
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("purchase_document_import")).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("purchase_document_import").assertIsDisplayed()
    }

    @Test
    fun documentSection_postedPurchase_showsReadOnlyDocument() {
        val now = Instant.now().toEpochMilli()
        val receiptId = "p_posted"
        runBlocking {
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INV-1",
                    purchaseDate = now,
                    status = DocumentStatus.POSTED.name,
                    notes = null,
                    attachmentPath = "attachments/purchases/p_posted/invoice.pdf",
                    createdAt = now,
                    updatedAt = now,
                    postedAt = now,
                    voidedAt = null
                )
            )
        }

        waitForHomeScreen()
        composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
        
        composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("purchase_document_section").assertIsDisplayed()
        
        val unavailableMsg = composeTestRule.activity.getString(R.string.purchase_document_unavailable)
        composeTestRule.onNodeWithText(unavailableMsg).assertIsDisplayed()
    }

    @Test
    fun complete_backup_restore_roundtrip_with_attachment() {
        runBlocking {
            val now = Instant.now().toEpochMilli()
            val receiptId = "p_roundtrip"
            // Use a filename that matches TestStateManager cleanup patterns
            val attachmentPath = "attachments/purchases/$receiptId/test_attachment_invoice.pdf"
            val attachmentFile = File(composeTestRule.activity.filesDir, attachmentPath)
            attachmentFile.parentFile?.mkdirs()
            attachmentFile.writeText("pdf-content")
            
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INV-RT",
                    purchaseDate = now,
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = attachmentPath,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            
            // 2. Create backup
            val backupFile = File(composeTestRule.activity.cacheDir, "test_roundtrip_backup.zip")
            val backupUri = backupFile.absolutePath
            val backupStatuses = backupRepository.createBackup(backupUri).toList()
            assertThat(backupStatuses.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
            
            // 3. Clear data
            testStateManager.resetAll()
            assertThat(database.purchaseDao().getReceiptById(receiptId)).isNull()
            assertThat(attachmentFile.exists()).isFalse()
            
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
            assertThat(restoredReceipt?.attachmentPath).isEqualTo(attachmentPath)
            assertThat(File(composeTestRule.activity.filesDir, attachmentPath).exists()).isTrue()
            assertThat(File(composeTestRule.activity.filesDir, attachmentPath).readText()).isEqualTo("pdf-content")
            
            // Cleanup backup file
            backupFile.delete()
        }
    }
}
