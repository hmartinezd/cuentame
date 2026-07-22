package com.miara.cuentame

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstScreen_isHome() {
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun navigateToInventory_showsInventoryPlaceholder() {
        val inventoryText = composeTestRule.activity.getString(R.string.nav_inventory)
        val inventoryTitle = composeTestRule.activity.getString(R.string.inventory_title)
        
        composeTestRule.onNodeWithText(inventoryText).performClick()
        
        // Use onAllNodesWithText because the title might appear in TopAppBar and the Screen
        composeTestRule.onAllNodesWithText(inventoryTitle)[0].assertIsDisplayed()
    }

    @Test
    fun navigateToSettings_showsSettingsPlaceholder() {
        val settingsDesc = composeTestRule.activity.getString(R.string.nav_settings)
        
        composeTestRule.onNodeWithContentDescription(settingsDesc).performClick()
        
        composeTestRule.onAllNodesWithText(settingsDesc)[0].assertIsDisplayed()
    }
}
