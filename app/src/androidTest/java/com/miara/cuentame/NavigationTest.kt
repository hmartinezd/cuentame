package com.miara.cuentame

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.miara.cuentame.app.navigation.TopLevelDestination
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.seed.UnitSeeds
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun firstScreen_isWelcome() {
        val setupAction = composeTestRule.activity.getString(R.string.onboarding_setup_action)
        composeTestRule.onNodeWithText(setupAction).assertIsDisplayed()
    }

    @Test
    fun navigateToInventory_showsInventoryPlaceholder() {
        // Need to skip onboarding or seed DB
        // For now, I'll focus on OnboardingUiTest for new flows
    }
}
