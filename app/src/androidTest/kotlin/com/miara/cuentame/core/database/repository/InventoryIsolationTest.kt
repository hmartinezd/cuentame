package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InventoryIsolationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: PreparationRecipeRepository

    @Inject
    lateinit var database: com.miara.cuentame.core.database.RestaurantInventoryDatabase

    private val restaurantId = RestaurantId("rest-1")

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun recipeOperationsLeaveInventoryUntouched() = runBlocking {
        // 1. Capture initial counts
        val initialCounts = captureInventoryCounts()

        // 2. Perform various recipe operations
        val outputId = IngredientId("ing-output")
        val compId = IngredientId("ing-comp")
        val yieldUnitId = IngredientUnitOptionId("opt-yield")
        val compUnitId = IngredientUnitOptionId("opt-comp")

        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(
            restaurantId = restaurantId,
            outputIngredientId = outputId,
            name = "Test Recipe",
            standardYieldQuantity = BigDecimal("10.0"),
            yieldUnitOptionId = yieldUnitId,
            notes = "Initial notes"
        ))

        repository.updateDraft(UpdatePreparationRecipeCommand(
            recipeId = recipeId,
            name = "Updated Recipe",
            standardYieldQuantity = BigDecimal("20.0"),
            yieldUnitOptionId = yieldUnitId,
            notes = "Updated notes"
        ))

        val componentId = repository.saveComponent(SavePreparationRecipeComponentCommand(
            recipeId = recipeId,
            componentId = null,
            componentIngredientId = compId,
            unitOptionId = compUnitId,
            quantityEntered = BigDecimal("5.0"),
            sortOrder = 1,
            notes = "Comp notes"
        ))

        repository.reorderComponents(recipeId, listOf(componentId))
        repository.activate(recipeId)
        repository.moveToDraft(recipeId)
        repository.archive(recipeId)
        repository.restoreToDraft(recipeId)

        // 3. Capture final counts
        val finalCounts = captureInventoryCounts()

        // 4. Verify exact equality
        assertThat(finalCounts).isEqualTo(initialCounts)
    }

    private fun captureInventoryCounts(): Map<String, Int> {
        val tables = listOf(
            "inventory_movements",
            "inventory_balance_projections",
            "ingredient_cost_projections",
            "purchase_receipts",
            "purchase_lines",
            "stock_counts",
            "waste_events"
        )
        return tables.associateWith { table ->
            val cursor = database.query("SELECT COUNT(*) FROM $table", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            count
        }
    }
}
