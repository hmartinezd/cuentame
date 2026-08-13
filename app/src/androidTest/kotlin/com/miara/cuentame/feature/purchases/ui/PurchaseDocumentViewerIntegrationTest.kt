package com.miara.cuentame.feature.purchases.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.backup.PurchaseAttachmentLocation
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class PurchaseDocumentViewerIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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

    private fun waitForHomeScreen() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun createPdf(file: File, pages: Int) {
        val document = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }

        for (i in 0 until pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawText("Test Page ${i + 1}", 100f, 100f, paint)
            document.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun seedPurchaseWithDocument(receiptId: String, fileName: String, pageCount: Int): String {
        val now = Instant.now().toEpochMilli()
        val receipt = PurchaseReceiptId(receiptId)
        
        val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(receipt, fileName)
        val file = PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        createPdf(file, pageCount)

        runBlocking {
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INV-PDF",
                    purchaseDate = now,
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = relativePath,
                    attachmentDisplayName = fileName,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }
        return receiptId
    }

    @Test(timeout = 60000)
    fun viewer_rendersOnePagePdf_andReturnsSafely() {
        val receiptId = "p_one_page"
        seedPurchaseWithDocument(receiptId, "one.pdf", 1)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
            
            composeTestRule.onNodeWithTag("purchase_document_view", useUnmergedTree = true).performClick()
            
            // Wait for viewer and page
            composeTestRule.onNodeWithTag("purchase_document_viewer").assertIsDisplayed()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_document_pdf_page_0")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("purchase_document_pdf_page_0").assertIsDisplayed()
            composeTestRule.onNodeWithTag("purchase_document_pdf_page_error_0").assertDoesNotExist()

            // Back button
            composeTestRule.onNodeWithTag("purchase_document_viewer_back").performClick()
            
            // Verify return to draft
            composeTestRule.onNodeWithTag("purchase_draft_screen").assertIsDisplayed()
        }
    }

    @Test(timeout = 60000)
    fun viewer_rendersMultipagePdf() {
        val receiptId = "p_multi_page"
        seedPurchaseWithDocument(receiptId, "multi.pdf", 3)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
            
            composeTestRule.onNodeWithTag("purchase_document_view", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_document_pdf_page_0")).fetchSemanticsNodes().isNotEmpty()
            }

            // Scroll to find last page
            composeTestRule.onNodeWithTag("purchase_document_pdf_list").performScrollToIndex(2)
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_document_pdf_page_2")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_document_pdf_page_2").assertIsDisplayed()
        }
    }

    @Test(timeout = 60000)
    fun viewer_immediateBack_doesNotCrash() {
        val receiptId = "p_race"
        seedPurchaseWithDocument(receiptId, "race.pdf", 1)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
            
            composeTestRule.onNodeWithTag("purchase_document_view", useUnmergedTree = true).performClick()
            
            // IMMEDIATELY press back without waiting for render
            composeTestRule.onNodeWithTag("purchase_document_viewer_back").performClick()
            
            // Verify return to draft
            composeTestRule.onNodeWithTag("purchase_draft_screen").assertIsDisplayed()
        }
    }

    @Test(timeout = 60000)
    fun viewer_renderError_doesNotCrashOnBack() {
        val receiptId = "p_error"
        val now = Instant.now().toEpochMilli()
        val receipt = PurchaseReceiptId(receiptId)
        
        // Seed an INVALID PDF file (just text)
        val relativePath = PurchaseAttachmentLocation.buildRelativeLocation(receipt, "invalid.pdf")
        val file = PurchaseAttachmentLocation.resolvePurchaseDocument(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("NOT A PDF")

        runBlocking {
            database.purchaseDao().insertReceipt(
                PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = TestSeeder.RESTAURANT_ID,
                    supplierId = null,
                    invoiceNumber = "INV-ERROR",
                    purchaseDate = now,
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = relativePath,
                    attachmentDisplayName = "invalid.pdf",
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
            
            composeTestRule.onNodeWithTag("purchase_document_view", useUnmergedTree = true).performClick()
            
            // Wait for inspector to fail (pageCount will be 0)
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_document_pdf_empty")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("purchase_document_viewer_back").performClick()
            composeTestRule.onNodeWithTag("purchase_draft_screen").assertIsDisplayed()
        }
    }
}
