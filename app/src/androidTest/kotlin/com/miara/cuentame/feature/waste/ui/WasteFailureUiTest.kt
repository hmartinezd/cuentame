package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.repository.IntegrationFailurePoints
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.ConfigurableAttachmentPermissionManager
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
class WasteFailureUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var failureBoundary: com.miara.cuentame.core.database.repository.IntegrationFailureBoundary

    @Inject
    lateinit var attachmentPermissionManager: com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restaurantId = "rest_fail_test"
    private val ingId = "ing_test"
    private val areaId = "area_test"
    private val optId = "opt_test"

    @Before
    fun setup() {
        hiltRule.inject()
        (failureBoundary as? ConfigurableFailureBoundary)?.reset()
        (attachmentPermissionManager as? ConfigurableAttachmentPermissionManager)?.shouldFail = false
        
        runBlocking {
            testStateManager.resetAll()

            val now = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en-US", now, now, null))
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Main Area", "main area", 0, true, now, now, null))
            database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken", "chicken", null, "mass_lb", areaId, null, null, null, true, now, now, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "Pound", "lb", null, BigDecimal.ONE, true, true, true, true, now, now, null))

            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.setAppLocaleTag("en-US")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            (failureBoundary as? ConfigurableFailureBoundary)?.reset()
            testStateManager.resetAll()
        }
    }

    private fun openWasteListScreen() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("view_waste_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("view_waste_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("waste_list_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openWasteDetailScreen(eventId: String) {
        openWasteListScreen()
        composeTestRule.onNodeWithTag("waste_item_$eventId").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("waste_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun wastePost_failureRollback_andRetrySuccess() {
        val now = Instant.parse("2026-01-01T12:05:00Z").toEpochMilli()
        val draftId = "waste_post_fail"

        runBlocking {
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = ingId,
                    areaId = areaId,
                    ingredientUnitOptionId = optId,
                    quantityEntered = "5.0",
                    quantityBase = "5.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = now,
                    notes = "Post fail test",
                    attachmentPath = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }

        val configBoundary = failureBoundary as ConfigurableFailureBoundary
        configBoundary.triggerOn(IntegrationFailurePoints.WASTE_POST_AFTER_MOVEMENT)

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteDetailScreen(draftId)

            // Click post
            composeTestRule.onNodeWithTag("waste_post_button").performClick()
            
            // Confirm dialog
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_post_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            // Verify error snackbar content
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("waste_error_snackbar_content")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_error_snackbar_content", useUnmergedTree = true).assertIsDisplayed()
            
            // Verify database state: should still be DRAFT due to transaction rollback
            runBlocking {
                val event = database.wasteDao().getById(draftId)
                assertThat(event?.status).isEqualTo(DocumentStatus.DRAFT.name)
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, draftId)
                assertThat(movements).isEmpty()
            }
            
            assertThat(configBoundary.triggerCount).isEqualTo(1)
            
            configBoundary.reset()
            
            // Confirm again in the same dialog
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_post_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            composeTestRule.waitUntil(10000) {
                val e = runBlocking { database.wasteDao().getById(draftId) }
                e?.status == DocumentStatus.POSTED.name
            }
            
            runBlocking {
                val event = database.wasteDao().getById(draftId)
                assertThat(event?.status).isEqualTo(DocumentStatus.POSTED.name)
                assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, draftId)).hasSize(1)
            }
            
            composeTestRule.onNodeWithTag("waste_post_confirm_dialog").assertDoesNotExist()
        }
    }

    @Test
    fun wasteVoid_failureRollback_andRetrySuccess() {
        val effectiveAt = Instant.parse("2026-01-01T11:55:00Z").toEpochMilli()
        val postedAt = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        val eventId = "waste_void_fail"

        runBlocking {
            database.wasteDao().insert(
                WasteEventEntity(
                    id = eventId,
                    restaurantId = restaurantId,
                    ingredientId = ingId,
                    areaId = areaId,
                    ingredientUnitOptionId = optId,
                    quantityEntered = "3.0",
                    quantityBase = "3.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = effectiveAt,
                    notes = null,
                    attachmentPath = null,
                    status = DocumentStatus.POSTED.name,
                    createdAt = effectiveAt,
                    updatedAt = postedAt,
                    postedAt = postedAt,
                    voidedAt = null
                )
            )
            database.inventoryMovementDao().insert(
                InventoryMovementEntity(
                    id = "mov_orig_1",
                    restaurantId = restaurantId,
                    ingredientId = ingId,
                    areaId = areaId,
                    movementType = InventoryMovementType.WASTE.name,
                    quantityBaseSigned = "-3.0",
                    unitCostBaseSnapshot = "2.50",
                    totalValueSnapshot = "-7.50",
                    effectiveAt = effectiveAt,
                    sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
                    sourceDocumentId = eventId,
                    sourceLineId = eventId,
                    sourceOperationId = "waste-post:$eventId",
                    reversalOfMovementId = null,
                    createdAt = postedAt
                )
            )
        }

        val configBoundary = failureBoundary as ConfigurableFailureBoundary
        configBoundary.triggerOn(IntegrationFailurePoints.WASTE_VOID_AFTER_REVERSAL)

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteDetailScreen(eventId)

            composeTestRule.onNodeWithTag("waste_void_button").performClick()
            
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_void_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            // Verify error snackbar content
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("waste_error_snackbar_content")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_error_snackbar_content", useUnmergedTree = true).assertIsDisplayed()
            
            // Verify database state: should still be POSTED
            runBlocking {
                val event = database.wasteDao().getById(eventId)
                assertThat(event?.status).isEqualTo(DocumentStatus.POSTED.name)
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)
                assertThat(movements).hasSize(1) // Only original
            }
            
            assertThat(configBoundary.triggerCount).isEqualTo(1)

            configBoundary.reset()
            
            // Retry
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_void_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("waste_list_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            runBlocking {
                assertThat(database.wasteDao().getById(eventId)?.status).isEqualTo(DocumentStatus.VOIDED.name)
                assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)).hasSize(2)
            }
        }
    }

    @Test
    fun wasteDelete_failureRollback_andRetrySuccess() {
        val now = Instant.parse("2026-01-01T12:05:00Z").toEpochMilli()
        val draftId = "waste_del_fail"

        runBlocking {
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = ingId,
                    areaId = areaId,
                    ingredientUnitOptionId = optId,
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.DROPPED_OR_DAMAGED.name,
                    effectiveAt = now,
                    notes = "Delete fail test",
                    attachmentPath = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }

        val configBoundary = failureBoundary as ConfigurableFailureBoundary
        configBoundary.triggerOn(IntegrationFailurePoints.WASTE_DELETE_AFTER_VALIDATION)

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteDetailScreen(draftId)
            
            composeTestRule.onNodeWithTag("waste_delete_button").performClick()
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_delete_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("waste_error_snackbar_content")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_error_snackbar_content", useUnmergedTree = true).assertIsDisplayed()
            
            runBlocking {
                assertThat(database.wasteDao().getById(draftId)).isNotNull()
            }
            assertThat(configBoundary.triggerCount).isEqualTo(1)

            configBoundary.reset()
            
            // Retry
            composeTestRule.onNode(
                hasTestTag("archive_confirm_button") and hasAnyAncestor(hasTestTag("waste_delete_confirm_dialog")),
                useUnmergedTree = true
            ).performClick()

            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithTag("waste_list_screen").fetchSemanticsNodes().isNotEmpty()
            }
            
            runBlocking {
                assertThat(database.wasteDao().getById(draftId)).isNull()
            }
        }
    }
}
