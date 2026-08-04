package com.miara.cuentame.feature.production.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDraftUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionBatchDraftScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionGuards_saveActive_disablesReviewAndDelete() {
        val uiState = ProductionBatchDraftUiState(
            screenState = ProductionBatchScreenState.Ready,
            isSaving = true,
            batch = mockBatch()
        )

        composeTestRule.setContent {
            ProductionBatchDraftScreen(
                uiState = uiState,
                onBackClick = {},
                onSaveClick = {},
                onDeleteClick = {},
                onReviewClick = {},
                onMultiplierChanged = {},
                onAreaSelected = {},
                onUnitOptionSelected = {},
                onActualOutputChanged = {},
                onEffectiveAtChanged = {},
                onNotesChanged = {},
                onOverrideOutput = {},
                onComponentClick = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithTag("production_batch_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("production_batch_delete").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("production_batch_review").assertIsNotEnabled()
    }

    @Test
    fun actionGuards_deleteActive_disablesSaveAndReview() {
        val uiState = ProductionBatchDraftUiState(
            screenState = ProductionBatchScreenState.Ready,
            isDeleting = true,
            batch = mockBatch()
        )

        composeTestRule.setContent {
            ProductionBatchDraftScreen(
                uiState = uiState,
                onBackClick = {},
                onSaveClick = {},
                onDeleteClick = {},
                onReviewClick = {},
                onMultiplierChanged = {},
                onAreaSelected = {},
                onUnitOptionSelected = {},
                onActualOutputChanged = {},
                onEffectiveAtChanged = {},
                onNotesChanged = {},
                onOverrideOutput = {},
                onComponentClick = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithTag("production_batch_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("production_batch_delete").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("production_batch_review").assertIsNotEnabled()
    }

    private fun mockBatch() = com.miara.cuentame.core.model.inventory.ProductionBatch(
        id = com.miara.cuentame.core.common.ids.ProductionBatchId("b1"),
        restaurantId = com.miara.cuentame.core.common.ids.RestaurantId("r1"),
        recipeId = com.miara.cuentame.core.common.ids.PreparationRecipeId("rec1"),
        recipeNameSnapshot = "Recipe",
        outputIngredientId = com.miara.cuentame.core.common.ids.IngredientId("i1"),
        batchMultiplier = java.math.BigDecimal.ONE,
        recipeStandardYieldQuantitySnapshot = java.math.BigDecimal.TEN,
        recipeStandardYieldBaseSnapshot = java.math.BigDecimal.TEN,
        recipeYieldUnitOptionIdSnapshot = com.miara.cuentame.core.common.ids.IngredientUnitOptionId("o1"),
        expectedOutputQuantityEntered = java.math.BigDecimal.TEN,
        expectedOutputQuantityBase = java.math.BigDecimal.TEN,
        actualOutputQuantityEntered = java.math.BigDecimal.TEN,
        actualOutputQuantityBase = java.math.BigDecimal.TEN,
        outputUnitOptionId = com.miara.cuentame.core.common.ids.IngredientUnitOptionId("o1"),
        outputAreaId = com.miara.cuentame.core.common.ids.InventoryAreaId("a1"),
        hasManualOutputQuantityOverride = false,
        totalComponentCostSnapshot = null,
        outputUnitCostBaseSnapshot = null,
        effectiveAt = java.time.Instant.now(),
        status = com.miara.cuentame.core.model.inventory.DocumentStatus.DRAFT,
        notes = null,
        components = emptyList(),
        createdAt = java.time.Instant.now(),
        updatedAt = java.time.Instant.now(),
        postedAt = null,
        voidedAt = null
    )
}
