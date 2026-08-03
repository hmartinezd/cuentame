package com.miara.cuentame.feature.production.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionNavigationStackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            NavHost(
                navController = navController,
                startDestination = Destination.PRODUCTION_BATCH_LIST.route
            ) {
                composable(Destination.PRODUCTION_BATCH_LIST.route) { Box(Modifier.testTag("list")) }
                composable(Destination.PRODUCTION_BATCH_CREATE.route) { Box(Modifier.testTag("create")) }
                composable(Destination.PRODUCTION_BATCH_DRAFT.route) { Box(Modifier.testTag("draft")) }
                composable(Destination.PRODUCTION_BATCH_COMPONENT.route) { Box(Modifier.testTag("component")) }
                composable(Destination.PRODUCTION_BATCH_PREVIEW.route) { Box(Modifier.testTag("preview")) }
                composable(Destination.PRODUCTION_BATCH_DETAIL.route) { Box(Modifier.testTag("detail")) }
            }
        }
    }

    @Test
    fun createToDraft_replacesCreate() {
        val batchId = ProductionBatchId("b1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(Destination.PRODUCTION_BATCH_CREATE.route)
        }
        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PRODUCTION_BATCH_CREATE.route)

        composeTestRule.runOnUiThread {
            navController.replaceProductionCreateWithDraft(batchId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PRODUCTION_BATCH_DRAFT.route)
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PRODUCTION_BATCH_CREATE.route }).isFalse()
    }

    @Test
    fun previewToPost_replacesDraftAndPreviewWithDetail() {
        val batchId = ProductionBatchId("b1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.productionBatchDraft(batchId))
            navController.navigate(AppRoutes.productionBatchPreview(batchId))
        }
        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PRODUCTION_BATCH_PREVIEW.route)

        composeTestRule.runOnUiThread {
            navController.replaceProductionPreviewWithDetail(batchId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PRODUCTION_BATCH_DETAIL.route)
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PRODUCTION_BATCH_DRAFT.route }).isFalse()
        assertThat(backStack.any { it.destination.route == Destination.PRODUCTION_BATCH_PREVIEW.route }).isFalse()
    }

    @Test
    fun draftExternalStatusChange_replacesDraftWithDetail() {
        val batchId = ProductionBatchId("b1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.productionBatchDraft(batchId))
        }

        composeTestRule.runOnUiThread {
            navController.replaceProductionDraftWithDetail(batchId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PRODUCTION_BATCH_DETAIL.route)
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PRODUCTION_BATCH_DRAFT.route }).isFalse()
    }
}
