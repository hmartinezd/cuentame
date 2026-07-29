package com.miara.cuentame.feature.waste.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.ConfigurableAttachmentPermissionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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

    private val restaurantId = "rest_fail_test"
    private val ingId = "ing_test"

    @Before
    fun setup() {
        hiltRule.inject()
        (failureBoundary as? com.miara.cuentame.core.database.repository.ConfigurableFailureBoundary)?.reset()
        (attachmentPermissionManager as? ConfigurableAttachmentPermissionManager)?.shouldFail = false
        
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()

            val now = Instant.now()
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Test Rest", "USD", "en", now.toEpochMilli(), now.toEpochMilli(), null))
            database.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            database.inventoryAreaDao().upsert(InventoryAreaEntity("area_test", restaurantId, "Area", "area", 0, true, now.toEpochMilli(), now.toEpochMilli(), null))
            database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken", "chicken", null, "mass_lb", "area_test", null, null, null, true, now.toEpochMilli(), now.toEpochMilli(), null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt_test", ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, now.toEpochMilli(), now.toEpochMilli(), null))

            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.setAppLocaleTag("en")
        }
    }

    @Test
    fun wastePost_showsGenericError_onUnexpectedFailure() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_waste_button").performClick()
            composeTestRule.onNodeWithTag("waste_list_screen").assertIsDisplayed()
        }
    }
}
