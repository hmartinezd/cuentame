package com.miara.cuentame.feature.purchases.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.database.repository.IntegrationFailurePoints
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class PurchaseFailureUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var failureBoundary: com.miara.cuentame.core.database.repository.IntegrationFailureBoundary

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    private val restaurantId = "rest_purchase_fail"
    private val ingId = "ing_test"
    private val areaId = "area_test"
    private val optId = "opt_test"

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            (failureBoundary as? ConfigurableFailureBoundary)?.reset()
            restoreGate.updateRecoveryState(RestoreStartupState.Ready)
            
            val now = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en-US", now, now, null))
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Area", "area", 0, true, now, now, null))
            database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken", "chicken", null, "mass_lb", areaId, null, null, null, true, now, now, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "Pound", "lb", null, BigDecimal.ONE, true, true, true, true, now, now, null))

            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.setAppLocaleTag("en-US")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @Test
    fun purchasePost_rollback_andRetry_onFailure() {
        val receiptId = "pur_fail_1"
        runBlocking {
            val now = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
            database.purchaseDao().insertReceipt(
                com.miara.cuentame.core.database.entity.PurchaseReceiptEntity(
                    id = receiptId,
                    restaurantId = restaurantId,
                    supplierId = null,
                    invoiceNumber = "INV-FAIL",
                    purchaseDate = now,
                    status = DocumentStatus.DRAFT.name,
                    notes = null,
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            database.purchaseDao().insertLine(
                com.miara.cuentame.core.database.entity.PurchaseLineEntity(
                    id = "line_1",
                    purchaseReceiptId = receiptId,
                    ingredientId = ingId,
                    areaId = areaId,
                    ingredientUnitOptionId = optId,
                    quantityEntered = "10.0",
                    quantityBase = "10.0",
                    lineTotal = "100.0",
                    unitCostBase = "10.0",
                    notes = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val configBoundary = failureBoundary as ConfigurableFailureBoundary
        configBoundary.triggerOn(IntegrationFailurePoints.PURCHASE_POST_AFTER_MOVEMENTS)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            // 1. Navigate to Purchases
            composeTestRule.onNodeWithTag("nav_purchases", useUnmergedTree = true).performClick()
            
            // 2. Open the draft
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_item_$receiptId")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_item_$receiptId").performClick()
            
            // 3. Trigger Post
            composeTestRule.onNodeWithTag("purchase_draft_list")
                .performScrollToNode(hasTestTag("purchase_post_button"))
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_post_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_post_button").performClick()
            
            // 4. Confirm dialog
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_post_confirm_dialog")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("purchase_post_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            // 5. Verify error snackbar content
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("purchase_error_snackbar_content")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("purchase_error_snackbar_content", useUnmergedTree = true).assertIsDisplayed()
            
            // 6. Verify database state: should still be DRAFT due to transaction rollback
            runBlocking {
                val receipt = database.purchaseDao().getReceiptById(receiptId)
                assertThat(receipt?.status).isEqualTo(DocumentStatus.DRAFT.name)
                assertThat(receipt?.postedAt).isNull()
                
                val line = database.purchaseDao().getLineById("line_1")
                assertThat(line?.quantityEntered).isEqualTo("10.0")
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
                assertThat(movements).isEmpty()

                val projection = database.inventoryProjectionDao().getBalance(ingId, areaId)
                assertThat(projection).isNull()
            }
            
            assertThat(configBoundary.triggerCount).isEqualTo(1)
            
            // Dialog must remain open
            composeTestRule.onNodeWithTag("purchase_post_confirm_dialog").assertIsDisplayed()

            // 7. Reset and Retry within the same dialog
            configBoundary.reset()
            
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("purchase_post_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()
            
            // 8. Verify successful transition
            composeTestRule.waitUntil(15000) {
                val r = runBlocking { database.purchaseDao().getReceiptById(receiptId) }
                r?.status == DocumentStatus.POSTED.name
            }
            
            runBlocking {
                val receipt = database.purchaseDao().getReceiptById(receiptId)
                assertThat(receipt?.postedAt).isNotNull()
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
                assertThat(movements).hasSize(1)
                assertThat(movements[0].movementType).isEqualTo(com.miara.cuentame.core.model.inventory.InventoryMovementType.PURCHASE.name)
                assertThat(movements[0].quantityBaseSigned).isEqualTo("10.0")

                val projection = database.inventoryProjectionDao().getBalance(ingId, areaId)
                assertThat(projection?.quantityBase).isEqualTo("10.0")
            }
            
            // Dialog should be closed
            composeTestRule.onNodeWithTag("purchase_post_confirm_dialog").assertDoesNotExist()
        }
    }
}
