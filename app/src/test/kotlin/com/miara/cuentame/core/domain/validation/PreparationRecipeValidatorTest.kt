package com.miara.cuentame.core.domain.validation

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.ingredient.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PreparationRecipeValidatorTest {

    private lateinit var validator: PreparationRecipeValidator

    @Before
    fun setup() {
        validator = PreparationRecipeValidator()
    }

    @Test
    fun `valid recipe passes activation validation`() {
        val recipe = createRecipe(outputId = "ing-1", yieldUnitId = "unit-y")
        val outputIng = createIngredient(id = "ing-1")
        val yieldUnit = createUnitOption(id = "unit-y", ingId = "ing-1")
        val components = listOf(
            createComponent(recipeId = "rec-1", componentId = "ing-2", unitId = "unit-2")
        )
        val allOutputUnitOptions = listOf(yieldUnit)
        val allComponentIngredients = mapOf("ing-2" to createIngredient(id = "ing-2"))
        val allComponentUnitOptions = mapOf("ing-2" to listOf(createUnitOption(id = "unit-2", ingId = "ing-2")))

        val failures = validator.validateActivation(
            recipe = recipe,
            outputIngredient = outputIng,
            components = components,
            allOutputUnitOptions = allOutputUnitOptions,
            allComponentIngredients = allComponentIngredients,
            allComponentUnitOptions = allComponentUnitOptions,
            existingGraphEdges = emptyList()
        )

        assertThat(failures).isEmpty()
    }

    @Test
    fun `missing output ingredient fails`() {
        val recipe = createRecipe(outputId = "ing-1")
        val failures = validator.validateActivation(
            recipe = recipe,
            outputIngredient = null,
            components = emptyList(),
            allOutputUnitOptions = emptyList(),
            allComponentIngredients = emptyMap(),
            allComponentUnitOptions = emptyMap(),
            existingGraphEdges = emptyList()
        )
        assertThat(failures).contains(PreparationRecipeValidationFailure.OutputIngredientNotFound)
    }

    @Test
    fun `direct cycle is rejected`() {
        val recipe = createRecipe(outputId = "ing-1", yieldUnitId = "unit-y")
        val components = listOf(
            createComponent(recipeId = "rec-1", componentId = "ing-1", unitId = "unit-y")
        )
        
        val failures = validator.validateActivation(
            recipe = recipe,
            outputIngredient = createIngredient(id = "ing-1"),
            components = components,
            allOutputUnitOptions = listOf(createUnitOption(id = "unit-y", ingId = "ing-1")),
            allComponentIngredients = mapOf("ing-1" to createIngredient(id = "ing-1")),
            allComponentUnitOptions = mapOf("ing-1" to listOf(createUnitOption(id = "unit-y", ingId = "ing-1"))),
            existingGraphEdges = emptyList()
        )
        assertThat(failures).contains(PreparationRecipeValidationFailure.ComponentCannotBeOutput)
    }

    @Test
    fun `two-recipe cycle is rejected`() {
        val recipe = createRecipe(id = "rec-2", outputId = "ing-2", yieldUnitId = "unit-y2")
        val components = listOf(
            createComponent(recipeId = "rec-2", componentId = "ing-1", unitId = "unit-y1")
        )
        
        val existingEdges = listOf(
            PreparationRecipeDependencyEdge("ing-1", "ing-2")
        )

        val failures = validator.validateActivation(
            recipe = recipe,
            outputIngredient = createIngredient(id = "ing-2"),
            components = components,
            allOutputUnitOptions = listOf(createUnitOption(id = "unit-y2", ingId = "ing-2")),
            allComponentIngredients = mapOf("ing-1" to createIngredient(id = "ing-1")),
            allComponentUnitOptions = mapOf("ing-1" to listOf(createUnitOption(id = "unit-y1", ingId = "ing-1"))),
            existingGraphEdges = existingEdges
        )
        assertThat(failures).contains(PreparationRecipeValidationFailure.RecipeWouldCreateCycle)
    }

    private fun createRecipe(
        id: String = "rec-1",
        restId: String = "rest-1",
        outputId: String,
        yieldUnitId: String? = null,
        status: PreparationRecipeStatus = PreparationRecipeStatus.DRAFT
    ) = PreparationRecipe(
        id = PreparationRecipeId(id),
        restaurantId = RestaurantId(restId),
        outputIngredientId = IngredientId(outputId),
        name = "Recipe",
        standardYieldQuantity = BigDecimal("1.0"),
        standardYieldQuantityBase = BigDecimal("1.0"),
        yieldUnitOptionId = yieldUnitId?.let { IngredientUnitOptionId(it) },
        status = status,
        notes = null,
        components = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null
    )

    private fun createIngredient(
        id: String,
        restId: String = "rest-1",
        name: String = "Ingredient"
    ) = Ingredient(
        id = IngredientId(id),
        restaurantId = RestaurantId(restId),
        name = name,
        normalizedName = name.lowercase(),
        categoryId = null,
        baseUnitId = UnitId("base"),
        defaultAreaId = null,
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null
    )

    private fun createUnitOption(
        id: String,
        ingId: String,
        name: String = "Unit"
    ) = IngredientUnitOption(
        id = IngredientUnitOptionId(id),
        ingredientId = IngredientId(ingId),
        displayName = name,
        shortLabel = name,
        standardUnitId = null,
        factorToBase = BigDecimal.ONE,
        isBase = false,
        isDefaultCount = false,
        isDefaultPurchase = false,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null
    )

    private fun createComponent(
        id: String = "comp-1",
        recipeId: String,
        componentId: String,
        unitId: String
    ) = PreparationRecipeComponent(
        id = PreparationRecipeComponentId(id),
        recipeId = PreparationRecipeId(recipeId),
        componentIngredientId = IngredientId(componentId),
        unitOptionId = IngredientUnitOptionId(unitId),
        quantityEntered = BigDecimal("1.0"),
        quantityBase = BigDecimal("1.0"),
        sortOrder = 0,
        notes = null
    )
}
