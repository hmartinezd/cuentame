package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.R
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.core.di.ConfigurableAttachmentPermissionManager
import com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
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

    private val restaurantId = "rest-fail"
    private val areaId = "area-fail"
    private val ingId = "ing-fail"
    private val unitId = "unit-fail"
    private val optId = "opt-fail"

    @Before
    fun setup() {
        hiltRule.inject()
        (failureBoundary as ConfigurableFailureBoundary).reset()
        (attachmentPermissionManager as ConfigurableAttachmentPermissionManager).shouldFail = false
        runBlocking {
            preferencesRepository.setAppLocaleTag("en")
            seedData()
        }
    }

    @org.junit.After
    fun teardown() {
        runBlocking {
            (failureBoundary as ConfigurableFailureBoundary).reset()
            (attachmentPermissionManager as ConfigurableAttachmentPermissionManager).shouldFail = false
            database.clearAllTables()
            preferencesRepository.clearAll()
        }
    }

    private fun seedData() = runBlocking {
        database.clearAllTables()
        preferencesRepository.setOnboardingCompleted(true)
        database.restaurantDao().insert(RestaurantEntity(restaurantId, "Fail Rest", "USD", "en", 0L, 0L, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Fail Area", "fail area", 1, true, 0L, 0L, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity(unitId, "Pound", "lb", "Mass", BigDecimal.ONE, true, 1)))
        database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Fail Ingredient", "fail ingredient", null, unitId, null, null, null, null, true, 0L, 0L, null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))

        // Seed 10 lb at $2/lb for projection rollback tests
        database.inventoryMovementDao().insert(InventoryMovementEntity(
            "seed-mov", restaurantId, ingId, areaId, InventoryMovementType.PURCHASE.name, "10.0", "2.0", "20.0",
            500L, SourceDocumentType.PURCHASE_RECEIPT.name, "seed-doc", "seed-line", "seed-op", null, 500L
        ))
        
        database.inventoryProjectionDao().upsert(com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity(
            restaurantId, ingId, areaId, "10.0", 500L
        ))
        database.ingredientCostProjectionDao().upsert(com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity(
            restaurantId, ingId, "2.0", 500L
        ))
    }

    @Test
    fun postFailure_provesRollback() {
        val boundary = failureBoundary as ConfigurableFailureBoundary
        boundary.failurePoint = "post-after-movement"

        val eventId = "event-fail-post"
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, ingId, areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()

            // Capture projections before state
            val beforeBalance = runBlocking { BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0") }
            val beforeCost = runBlocking { BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0") }
            val beforeMovementCount = runBlocking { database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId).size }

            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Fail Ingredient", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_post_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()

            // Verify Error appeared and dialog remained
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.error_generic), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(isDialog() and hasAnyDescendant(hasText(context.getString(R.string.post_waste)))).assertIsDisplayed()

            // Prove Rollback
            runBlocking {
                val event = database.wasteDao().getById(eventId)
                assertThat(event!!.status).isEqualTo(DocumentStatus.DRAFT.name)
                assertThat(event.postedAt).isNull()
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)
                assertThat(movements.size).isEqualTo(beforeMovementCount)
                
                assertThat(boundary.triggerCount).isEqualTo(1)

                // Projections must remain equal to before-state
                val afterFailBalance = BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0")
                val afterFailCost = BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0")
                assertThat(afterFailBalance.compareTo(beforeBalance)).isEqualTo(0)
                assertThat(afterFailCost.compareTo(beforeCost)).isEqualTo(0)
            }

            // Retry after clearing failure
            boundary.reset()
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_posted)).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify Post success state
            runBlocking {
                val event = database.wasteDao().getById(eventId)
                assertThat(event!!.status).isEqualTo(DocumentStatus.POSTED.name)
                assertThat(event.postedAt).isNotNull()

                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)
                assertThat(movements).hasSize(1)

                val afterRetryBalance = BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0")
                val afterRetryCost = BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0")
                
                // Balance should reflect -5 lb waste (10 - 5 = 5)
                assertThat(afterRetryBalance.compareTo(BigDecimal("5.0"))).isEqualTo(0)
                // Cost should remain $2.0
                assertThat(afterRetryCost.compareTo(BigDecimal("2.0"))).isEqualTo(0)
            }
        }
    }

    @Test
    fun voidFailure_provesRollback() {
        val boundary = failureBoundary as ConfigurableFailureBoundary
        boundary.failurePoint = "void-after-reversal"

        val eventId = "event-fail-void"
        val postedAt = 2000L // after seed purchase at 500L
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, ingId, areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 2500L, null, null, DocumentStatus.POSTED.name,
                500L, postedAt, postedAt, null
            ))
            database.inventoryMovementDao().insert(InventoryMovementEntity(
                "mov-waste", restaurantId, ingId, areaId, InventoryMovementType.WASTE.name, "-5.0", "2.0", "-10.0",
                2500L, SourceDocumentType.WASTE_EVENT.name, eventId, eventId, "waste-post:$eventId", null, postedAt
            ))
            
            database.inventoryProjectionDao().upsert(com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity(
                restaurantId, ingId, areaId, "5.0", postedAt
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()

            // Capture projections before state
            val beforeBalance = runBlocking { BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0") }
            val beforeCost = runBlocking { BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0") }
            val beforeMovementCount = runBlocking { database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId).size }

            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Fail Ingredient", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_void_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.error_generic), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(isDialog() and hasAnyDescendant(hasText(context.getString(R.string.void_waste)))).assertIsDisplayed()

            // Prove Rollback
            runBlocking {
                val event = database.wasteDao().getById(eventId)
                assertThat(event!!.status).isEqualTo(DocumentStatus.POSTED.name)
                assertThat(event.voidedAt).isNull()
                
                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)
                assertThat(movements.size).isEqualTo(beforeMovementCount) // Only original WASTE remains
                assertThat(movements[0].movementType).isEqualTo(InventoryMovementType.WASTE.name)
                
                assertThat(boundary.triggerCount).isEqualTo(1)

                // Projections must remain equal to before-state
                val afterFailBalance = BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0")
                val afterFailCost = BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0")
                assertThat(afterFailBalance.compareTo(beforeBalance)).isEqualTo(0)
                assertThat(afterFailCost.compareTo(beforeCost)).isEqualTo(0)
            }

            // Retry
            boundary.reset()
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithText(context.getString(R.string.status_voided)).fetchSemanticsNodes().isNotEmpty()
            }

            // Verify Void success state
            runBlocking {
                val event = database.wasteDao().getById(eventId)
                assertThat(event!!.status).isEqualTo(DocumentStatus.VOIDED.name)
                assertThat(event.voidedAt).isNotNull()

                val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, eventId)
                assertThat(movements).hasSize(2) // WASTE + REVERSAL

                val afterRetryBalance = BigDecimal(database.inventoryProjectionDao().getBalance(ingId, areaId)?.quantityBase ?: "0")
                val afterRetryCost = BigDecimal(database.ingredientCostProjectionDao().getCost(ingId)?.averageUnitCostBase ?: "0")
                
                // Balance should be restored to 10 lb
                assertThat(afterRetryBalance.compareTo(BigDecimal("10.0"))).isEqualTo(0)
                // Cost remains $2.0
                assertThat(afterRetryCost.compareTo(BigDecimal("2.0"))).isEqualTo(0)
            }
        }
    }

    @Test
    fun deleteFailure_provesIntegrity() {
        val boundary = failureBoundary as ConfigurableFailureBoundary
        boundary.failurePoint = "delete-after-validation"

        val eventId = "event-fail-delete"
        runBlocking {
            database.wasteDao().insert(WasteEventEntity(
                eventId, restaurantId, ingId, areaId, optId, "5.0", "5.0",
                WasteReason.SPOILED.name, 1000L, null, null, DocumentStatus.DRAFT.name,
                500L, 500L, null, null
            ))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            waitForHome()
            composeTestRule.onNodeWithTag("view_waste_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasText("Fail Ingredient", substring = true) and hasClickAction()).onFirst().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("waste_detail_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(context.getString(R.string.delete_waste_draft)).performClick()
            composeTestRule.waitForIdle()
            
            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodesWithTag("archive_confirm_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(60000) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.error_generic), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify event still exists
            runBlocking {
                assertThat(database.wasteDao().getById(eventId)).isNotNull()
                assertThat(boundary.triggerCount).isEqualTo(1)
            }

            // Retry
            boundary.reset()
            composeTestRule.onNodeWithTag("archive_confirm_button").performClick()
            composeTestRule.waitForIdle()
            
            // Should be back at list and item gone
            composeTestRule.waitUntil(90000) {
                composeTestRule.onAllNodesWithTag("waste_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_item_$eventId").assertDoesNotExist()
        }
    }

    private fun waitForHome() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.home_title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }
}
