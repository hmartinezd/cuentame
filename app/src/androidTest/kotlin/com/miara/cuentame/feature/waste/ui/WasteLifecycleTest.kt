package com.miara.cuentame.feature.waste.ui

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
class WasteLifecycleTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun navigateToWasteAndBack() {
        composeTestRule.onNodeWithTag("home_waste_button").performClick()
        composeTestRule.onNodeWithTag("waste_list_screen").assertExists()
        
        composeTestRule.onNodeWithTag("waste_back_button").performClick()
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }
}
