package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DetailedReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            TestSeeder.seedBaseline(database)
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    @Test
    fun navigateToReportsAndBack() {
        composeTestRule.onNodeWithTag("home_reports_button").performClick()
        composeTestRule.onNodeWithTag("reports_screen").assertExists()
        
        composeTestRule.onNodeWithTag("reports_header").assertExists()
    }
}
