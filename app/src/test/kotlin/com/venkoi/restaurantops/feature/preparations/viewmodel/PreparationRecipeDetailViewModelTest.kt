package com.venkoi.restaurantops.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.PreparationRecipeId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.IngredientRepository
import com.venkoi.restaurantops.core.domain.repository.PreparationRecipeRepository
import com.venkoi.restaurantops.core.domain.repository.PreparationCostRepository
import com.venkoi.restaurantops.core.model.ingredient.*
import com.venkoi.restaurantops.core.model.ingredient.IngredientUnitOption
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipe
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PreparationRecipeDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preparationRecipeRepository = mockk<PreparationRecipeRepository>(relaxed = true)
    private val ingredientRepository = mockk<IngredientRepository>(relaxed = true)
    
    private val restaurantId = RestaurantId("r1")
    private lateinit var viewModel: PreparationRecipeDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads recipe and names`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val option = createUnitOption("o1", "out1")
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getById(IngredientId("out1")) } returns mockk { every { name } returns "Output Name" }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("out1"), true) } returns listOf(option)

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val state = viewModel.uiState.filter { it.loadState is PreparationScreenLoadState.EditReady }.first()

        assertThat(state.recipe).isEqualTo(recipe)
        assertThat(state.outputIngredientName).isEqualTo("Output Name")
        assertThat(state.yieldUnitLabel).isEqualTo("Opt o1")
    }

    @Test
    fun `moveToDraft calls repository and emits NavigateToEditor event`() = runTest {
        val recipeId = PreparationRecipeId("rec1")
        every { preparationRecipeRepository.observeRecipe(recipeId) } returns flowOf(createRecipe("rec1", "out1").copy(status = PreparationRecipeStatus.ACTIVE))

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        
        val events = mutableListOf<PreparationRecipeDetailEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onMoveToDraft()
        advanceUntilIdle()

        coVerify { preparationRecipeRepository.moveToDraft(recipeId) }
        assertThat(events).contains(PreparationRecipeDetailEvent.NavigateToEditor(recipeId))
        job.cancel()
    }

    @Test
    fun `onActivate - guards against concurrent operations`() = runTest {
        val recipeId = PreparationRecipeId("rec1")
        every { preparationRecipeRepository.observeRecipe(recipeId) } returns flowOf(createRecipe("rec1", "out1"))
        coEvery { preparationRecipeRepository.activate(any()) } coAnswers {
            testDispatcher.scheduler.advanceTimeBy(1000)
        }

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        advanceUntilIdle()

        viewModel.onActivate() // Starts operation
        viewModel.onActivate() // Should be ignored

        advanceUntilIdle()
        coVerify(exactly = 1) { preparationRecipeRepository.activate(any()) }
    }

    @Test
    fun `onRetry refreshes observation after failure`() = runTest {
        val recipeId = PreparationRecipeId("rec1")
        val recipe = createRecipe("rec1", "out1")
        val option = createUnitOption("o1", "out1")
        var callCount = 0
        every { preparationRecipeRepository.observeRecipe(recipeId) } answers {
            callCount++
            if (callCount == 1) flow { throw RuntimeException("Fail") }
            else flowOf(recipe)
        }
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Output Name" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(option)

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)

        viewModel.onRetry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.EditReady)
        assertThat(viewModel.uiState.value.recipe).isNotNull()
    }

    @Test
    fun `enrichment failure triggers LoadError and is retryable`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val option = createUnitOption("o1", "out1")
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        
        // Enrichment fails initially
        coEvery { ingredientRepository.getById(IngredientId("out1")) } throws RuntimeException("Enrichment failed")
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(option)

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isInstanceOf(PreparationScreenLoadState.LoadError::class.java)

        // Retry succeeds
        coEvery { ingredientRepository.getById(IngredientId("out1")) } returns mockk { every { name } returns "Output Name" }
        
        viewModel.onRetry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.EditReady)
        assertThat(viewModel.uiState.value.outputIngredientName).isEqualTo("Output Name")
    }

    @Test
    fun `cost states last production and reactive updates are preserved`() = runTest {
        val recipe = createRecipe("rec1", "out1")
        val option = createUnitOption("o1", "out1")
        val costs = MutableStateFlow(cost(PreparationCostStatus.FULLY_COSTED, BigDecimal.TEN))
        val costRepository = mockk<PreparationCostRepository>()
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        every { costRepository.observeRecipeCost(recipe.id) } returns costs
        coEvery { ingredientRepository.getById(IngredientId("out1")) } returns mockk { every { name } returns "Output Name" }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("out1"), true) } returns listOf(option)

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, SavedStateHandle(mapOf("recipeId" to "rec1")), costRepository
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentCost?.status).isEqualTo(PreparationCostStatus.FULLY_COSTED)
        assertThat(viewModel.uiState.value.currentCost?.lastProduction).isNotNull()

        costs.value = cost(PreparationCostStatus.PARTIALLY_COSTED, null)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentCost?.status).isEqualTo(PreparationCostStatus.PARTIALLY_COSTED)

        costs.value = cost(PreparationCostStatus.UNCOSTED, null).copy(
            components = listOf(componentWithReason(PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_YIELD_UNAVAILABLE))
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentCost?.components?.single()?.missingReason)
            .isEqualTo(PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_YIELD_UNAVAILABLE)
    }

    @Test
    fun `route classification - missing or blank recipeId results in InvalidRoute`() = runTest {
        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to ""))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.InvalidRoute)
        verify(exactly = 0) { preparationRecipeRepository.observeRecipe(any()) }
    }

    @Test
    fun `route classification - valid missing ID results in RecipeNotFound`() = runTest {
        val recipeId = PreparationRecipeId("missing")
        every { preparationRecipeRepository.observeRecipe(recipeId) } returns flowOf(null)

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "missing"))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.RecipeNotFound)
        verify(exactly = 1) { preparationRecipeRepository.observeRecipe(recipeId) }
    }

    private fun createUnitOption(id: String, ingId: String) = IngredientUnitOption(
        id = IngredientUnitOptionId(id),
        ingredientId = IngredientId(ingId),
        displayName = "Opt $id",
        shortLabel = "o",
        standardUnitId = null,
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null
    )

    private fun createRecipe(id: String, outputId: String) = PreparationRecipe(
        id = PreparationRecipeId(id),
        restaurantId = restaurantId,
        outputIngredientId = IngredientId(outputId),
        name = "Recipe $id",
        standardYieldQuantity = BigDecimal.ONE,
        standardYieldQuantityBase = BigDecimal.ONE,
        yieldUnitOptionId = IngredientUnitOptionId("o1"),
        status = PreparationRecipeStatus.DRAFT,
        notes = null,
        components = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null
    )

    private fun cost(status: PreparationCostStatus, total: BigDecimal?) = PreparationRecipeCost(
        PreparationRecipeId("rec1"), status, 1, if (total != null) 1 else 0, if (total == null) 1 else 0,
        total ?: BigDecimal.ONE, total, BigDecimal.ONE, "lb", total, total, "lb", emptySet(),
        emptyList(), PreparationPriceImpact(BigDecimal.ZERO, 0, 1),
        HistoricalPreparationCost(com.venkoi.restaurantops.core.common.ids.ProductionBatchId("batch"), Instant.EPOCH, BigDecimal.TEN, BigDecimal.ONE)
    )

    private fun componentWithReason(reason: PreparationCostMissingReason) = PreparationComponentCost(
        com.venkoi.restaurantops.core.common.ids.PreparationRecipeComponentId("component"), IngredientId("nested"), "Nested",
        BigDecimal.ONE, "lb", BigDecimal.ONE, "lb", null, null, null, reason, null
    )
}
