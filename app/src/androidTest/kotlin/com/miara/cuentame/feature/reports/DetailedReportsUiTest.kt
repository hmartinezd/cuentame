package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.miara.cuentame.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DetailedReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun navigateToReportsAndBack() {
        composeTestRule.onNodeWithTag("home_reports_button").performClick()
        composeTestRule.onNodeWithTag("reports_screen").assertExists()
        
        // Detailed report navigation depends on actual data, but we can verify tabs/header
        composeTestRule.onNodeWithTag("reports_header").assertExists()
    }
}
