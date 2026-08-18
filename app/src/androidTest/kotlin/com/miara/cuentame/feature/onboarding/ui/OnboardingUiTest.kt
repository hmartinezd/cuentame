package com.miara.cuentame.feature.onboarding.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OnboardingUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restaurantDao: RestaurantDao

    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking { testStateManager.resetAll() }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun onboarding_start_navigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()

            // Welcome Step visibility
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_welcome_content", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun onboarding_languageChange_isLiveAndPreservesRestaurantName() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_setup_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).performClick()
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_restaurant_name", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true)
                .performTextInput("La Taqueria")

            composeTestRule.onNodeWithTag("onboarding_language_selector", useUnmergedTree = true)
                .performClick()
            composeTestRule.onNodeWithText("Español", useUnmergedTree = true).performClick()

            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithText("Detalles del Restaurante", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Detalles del Restaurante", useUnmergedTree = true)
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true)
                .assertTextContains("La Taqueria")

            runBlocking {
                val prefs = appPreferencesRepository.observePreferences().first()
                assertThat(prefs.onboardingCompleted).isFalse()
                assertThat(prefs.appLocaleTag).isEqualTo("es-US")
                assertThat(appPreferencesRepository.loadOnboardingDraft()?.restaurantName)
                    .isEqualTo("La Taqueria")
            }
        }
    }

    @Test
    fun onboarding_fullFlow_persistsRestaurantAndNavigatesToHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()

            // 1. Welcome step: click setup
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_setup_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).performClick()

            // 2. Restaurant step: enter restaurant name
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_restaurant_name", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true).performTextInput("La Taqueria")
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 3. Areas step: click next
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_areas_title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 4. Categories step: click next
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_categories_title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 5. Review step: click finish
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_finish_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_finish_button", useUnmergedTree = true).performClick()

            // 6. Home screen appears
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("home_screen", useUnmergedTree = true).assertIsDisplayed()

            // Verify persistence in Room & DataStore
            runBlocking {
                val restaurant = restaurantDao.getRestaurant()
                assertThat(restaurant).isNotNull()
                assertThat(restaurant?.name).isEqualTo("La Taqueria")
                assertThat(restaurant?.currencyCode).isEqualTo("USD")
                assertThat(restaurant?.localeTag).isEqualTo("en-US")

                val prefs = appPreferencesRepository.observePreferences().first()
                assertThat(prefs.onboardingCompleted).isTrue()
            }

            // Verify activity recreation stays on Home screen
            scenario.recreate()
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("home_screen", useUnmergedTree = true).assertIsDisplayed()
        }

        // Verify second application launch begins on Home screen directly
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("home_screen", useUnmergedTree = true).assertIsDisplayed()
        }
    }
}
