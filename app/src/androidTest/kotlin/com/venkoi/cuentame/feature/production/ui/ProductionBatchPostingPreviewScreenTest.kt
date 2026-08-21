package com.venkoi.cuentame.feature.production.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import com.venkoi.cuentame.feature.production.viewmodel.ProductionBatchPreviewUiState
import com.venkoi.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import org.junit.Assert.assertTrue
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionBatchPostingPreviewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun errorPresentation_displaysInlineErrorAndDismiss() {
        val inlineError = UiMessage.Resource(R.string.error_generic)
        val uiState = ProductionBatchPreviewUiState(
            screenState = ProductionBatchScreenState.Ready,
            inlineError = inlineError,
            preview = mockkPreview() // We need a non-null preview for Ready state
        )

        var dismissCount = 0

        composeTestRule.setContent {
            ProductionBatchPostingPreviewScreen(
                uiState = uiState,
                onBackClick = {},
                onPostClick = {},
                onRetry = {},
                onClearError = { dismissCount++ }
            )
        }

        composeTestRule.onNodeWithTag("production_preview_inline_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("production_preview_inline_error_dismiss")
            .assertIsDisplayed()
            .performClick()

        org.junit.Assert.assertEquals(1, dismissCount)
    }

    @Test
    fun failureStateTags_areUniqueAndPresent() {
        val state = mutableStateOf<ProductionBatchScreenState>(ProductionBatchScreenState.InvalidRoute)

        composeTestRule.setContent {
            ProductionBatchPostingPreviewScreen(
                uiState = ProductionBatchPreviewUiState(screenState = state.value),
                onBackClick = {},
                onPostClick = {},
                onRetry = {},
                onClearError = {}
            )
        }

        val testStates = listOf(
            ProductionBatchScreenState.InvalidRoute to "production_preview_invalid_route",
            ProductionBatchScreenState.BatchNotFound to "production_preview_batch_not_found",
            ProductionBatchScreenState.ComponentNotFound to "production_preview_component_not_found",
            ProductionBatchScreenState.ParentNotEditable to "production_preview_parent_not_editable",
            ProductionBatchScreenState.LoadError(UiMessage.Resource(R.string.error_generic)) to "production_preview_load_error"
        )

        testStates.forEach { (s, tag) ->
            state.value = s
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    private fun mockkPreview() = com.venkoi.cuentame.core.domain.repository.ProductionBatchPostingPreview(
        batchId = com.venkoi.cuentame.core.common.ids.ProductionBatchId("b1"),
        effectiveAt = java.time.Instant.EPOCH,
        components = emptyList(),
        totalComponentCost = java.math.BigDecimal.TEN,
        actualOutputQuantityBase = java.math.BigDecimal.TEN,
        outputUnitCostBase = java.math.BigDecimal.ONE,
        yieldVariancePercent = java.math.BigDecimal.ZERO,
        blockers = emptyList()
    )
}
