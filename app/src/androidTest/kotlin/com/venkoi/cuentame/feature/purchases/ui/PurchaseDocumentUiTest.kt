package com.venkoi.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.backup.api.RestoreStartupState
import com.venkoi.cuentame.core.backup.internal.RestoreOperationGate
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.PurchaseReceiptEntity
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.preferences.repository.AppPreferencesRepository
import com.venkoi.cuentame.test.TestSeeder
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class PurchaseDocumentUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restoreGate: RestoreOperationGate

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

    @Test
    fun documentSection_showsSaveMessageBeforeFirstSave() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("add_purchase_fab")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_purchase_fab", useUnmergedTree = true).performClick()
            
            composeTestRule.onNodeWithTag("purchase_document_section").assertIsDisplayed()
            val saveMsg = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.purchase_save_header_first)
            composeTestRule.onNodeWithText(saveMsg).assertIsDisplayed()
        }
    }

    @Test
    fun documentSection_showsCaptureActionsAfterFirstSave() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForHomeScreen()
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("add_purchase_fab")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("add_purchase_fab", useUnmergedTree = true).performClick()
            
            composeTestRule.onNodeWithTag("purchase_header_save").performClick()
            
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_document_scan")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("purchase_document_scan").assertIsDisplayed()
            composeTestRule.onNodeWithTag("purchase_document_choose_file").assertIsDisplayed()
        }
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
                    attachmentDisplayName = null,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = now,
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
            
            composeTestRule.onNodeWithTag("purchase_detail_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("purchase_document_section").assertIsDisplayed()
            
            val unavailableMsg = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.purchase_document_unavailable)
            composeTestRule.onNodeWithText(unavailableMsg).assertIsDisplayed()
        }
    }
}
