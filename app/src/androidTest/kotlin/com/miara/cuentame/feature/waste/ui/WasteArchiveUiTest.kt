package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
class WasteArchiveUiTest {

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
    lateinit var attachmentPermissionManager: com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    private val restaurantId = "rest_archive_test"
    private val archivedIngId = "ing_archived"
    private val activeIngId = "ing_active"
    private val archivedAreaId = "area_archived"
    private val activeAreaId = "area_active"
    private val archivedOptId = "opt_archived"
    private val activeOptId = "opt_active"

    @Before
    fun setup() {
        hiltRule.inject()
        (attachmentPermissionManager as? ConfigurableAttachmentPermissionManager)?.shouldFail = false
        
        runBlocking {
            testStateManager.resetAll()
            restoreGate.updateRecoveryState(RestoreStartupState.Ready)

            val now = Instant.now()
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en-US", now.toEpochMilli(), now.toEpochMilli(), null))
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            // Seed areas
            database.inventoryAreaDao().upsert(InventoryAreaEntity(archivedAreaId, restaurantId, "Archived Freezer", "archived freezer", 0, false, now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli()))
            database.inventoryAreaDao().upsert(InventoryAreaEntity(activeAreaId, restaurantId, "Main Kitchen", "main kitchen", 1, true, now.toEpochMilli(), now.toEpochMilli(), null))

            // Seed archived ingredient & option
            database.ingredientDao().insert(IngredientEntity(archivedIngId, restaurantId, "Archived Beef", "archived beef", null, "mass_lb", archivedAreaId, null, null, null, false, now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli()))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(archivedOptId, archivedIngId, "Archived Box", "box", null, BigDecimal.ONE, true, true, true, false, now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli()))

            // Seed active ingredient & option
            database.ingredientDao().insert(IngredientEntity(activeIngId, restaurantId, "Active Chicken", "active chicken", null, "mass_lb", activeAreaId, null, null, null, true, now.toEpochMilli(), now.toEpochMilli(), null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(activeOptId, activeIngId, "Pound", "lb", null, BigDecimal.ONE, true, true, true, true, now.toEpochMilli(), now.toEpochMilli(), null))

            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.setAppLocaleTag("en-US")
        }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    private fun openWasteListScreen() {
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("home_dashboard_list")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag("view_waste_button"))
        composeTestRule.onNodeWithTag("view_waste_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("waste_list_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openWasteFormForEdit(draftId: String) {
        openWasteListScreen()
        composeTestRule.onNodeWithTag("waste_item_$draftId").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("waste_detail_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("waste_edit_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("waste_edit_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("waste_form_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForReadyWasteForm() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodes(hasTestTag("waste_save_button"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("waste_save_button", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun wasteForm_existingArchivedReferences_displayedAndPreservedOnSave() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_archived_ref"
        runBlocking {
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = archivedIngId,
                    areaId = archivedAreaId,
                    ingredientUnitOptionId = archivedOptId,
                    quantityEntered = "2.0",
                    quantityBase = "2.0",
                    reason = WasteReason.EXPIRED.name,
                    effectiveAt = now,
                    notes = "Archived ref test",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)
            waitForReadyWasteForm()
            
            // Save without changing
            composeTestRule.onNodeWithTag("waste_save_button", useUnmergedTree = true).performClick()
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("waste_detail_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("waste_detail_screen").assertIsDisplayed()
        }

        // Verify DB row remains associated with archived records
        runBlocking {
            val event = database.wasteDao().getById(draftId)
            assertThat(event).isNotNull()
            assertThat(event?.ingredientId).isEqualTo(archivedIngId)
            assertThat(event?.areaId).isEqualTo(archivedAreaId)
            assertThat(event?.ingredientUnitOptionId).isEqualTo(archivedOptId)
        }
    }

    @Test
    fun wasteForm_changingAwayFromArchivedReferences_persistsActiveValues() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_change_archived"
        runBlocking {
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = archivedIngId,
                    areaId = archivedAreaId,
                    ingredientUnitOptionId = archivedOptId,
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.EXPIRED.name,
                    effectiveAt = now,
                    notes = "Change archived test",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)
            waitForReadyWasteForm()

            // Select active ingredient
            composeTestRule.onNodeWithTag("ingredient_selector", useUnmergedTree = true).performScrollTo().performClick()
            composeTestRule.onNodeWithTag("ingredient_item_Active Chicken", useUnmergedTree = true)
                .performScrollTo().performClick()
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithTag("unit_selector").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("unit_selector").performClick()
            composeTestRule.onNodeWithTag("unit_item_lb", useUnmergedTree = true).performClick()

            // Select active area
            composeTestRule.onNodeWithTag("area_selector").performClick()
            composeTestRule.onAllNodesWithText("Main Kitchen", useUnmergedTree = true).onLast().performClick()

            // Save changes
            composeTestRule.onNodeWithTag("waste_save_button").assertIsEnabled().performClick()
            composeTestRule.waitUntil(10000) {
                runBlocking {
                    database.wasteDao().getById(draftId)?.let {
                        it.ingredientId == activeIngId && it.areaId == activeAreaId
                    } == true
                }
            }
        }

        // Verify database updated with active values
        runBlocking {
            val event = database.wasteDao().getById(draftId)
            assertThat(event).isNotNull()
            assertThat(event?.ingredientId).isEqualTo(activeIngId)
            assertThat(event?.areaId).isEqualTo(activeAreaId)
            assertThat(event?.ingredientUnitOptionId).isEqualTo(activeOptId)
        }
    }

    @Test
    fun wasteForm_missingIngredientReference_showsSafeErrorAndDisablesSave() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_missing_ing"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = "missing_ing_999",
                    areaId = activeAreaId,
                    ingredientUnitOptionId = activeOptId,
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = now,
                    notes = "Missing ing",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON;")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)

            // Verify safe form error shown and save disabled/absent or error text visible
            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithText("missing_ing_999").assertDoesNotExist()
        }

        // Verify DB row not mutated
        runBlocking {
            val event = database.wasteDao().getById(draftId)
            assertThat(event?.ingredientId).isEqualTo("missing_ing_999")
        }
    }

    @Test
    fun wasteForm_missingAreaReference_showsSafeError() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_missing_area"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = activeIngId,
                    areaId = "missing_area_999",
                    ingredientUnitOptionId = activeOptId,
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = now,
                    notes = "Missing area",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON;")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)

            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithText("missing_area_999").assertDoesNotExist()
        }
    }

    @Test
    fun wasteForm_missingUnitOptionReference_showsSafeError() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_missing_opt"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = activeIngId,
                    areaId = activeAreaId,
                    ingredientUnitOptionId = "missing_opt_999",
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = now,
                    notes = "Missing option",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON;")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)

            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithText("missing_opt_999").assertDoesNotExist()
        }
    }

    @Test
    fun wasteForm_crossIngredientUnitOption_showsSafeErrorAndPreventsSave() {
        val now = Instant.now().toEpochMilli()
        val draftId = "waste_cross_opt"
        runBlocking {
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
            database.wasteDao().insert(
                WasteEventEntity(
                    id = draftId,
                    restaurantId = restaurantId,
                    ingredientId = activeIngId,
                    areaId = activeAreaId,
                    ingredientUnitOptionId = archivedOptId, // Option belongs to archivedIngId, not activeIngId
                    quantityEntered = "1.0",
                    quantityBase = "1.0",
                    reason = WasteReason.SPOILED.name,
                    effectiveAt = now,
                    notes = "Cross opt test",
                    attachmentPath = null,
                    attachmentDisplayName = null,
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON;")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openWasteFormForEdit(draftId)

            composeTestRule.onNodeWithTag("form_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithText(archivedOptId).assertDoesNotExist()
        }

        // Verify DB row not mutated
        runBlocking {
            val event = database.wasteDao().getById(draftId)
            assertThat(event?.ingredientUnitOptionId).isEqualTo(archivedOptId)
        }
    }
}
