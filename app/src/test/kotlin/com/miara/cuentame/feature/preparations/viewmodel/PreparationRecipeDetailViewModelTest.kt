package com.miara.cuentame.feature.preparations.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipe
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        every { preparationRecipeRepository.observeRecipe(recipe.id) } returns flowOf(recipe)
        coEvery { ingredientRepository.getById(IngredientId("out1")) } returns mockk { every { name } returns "Output Name" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()

        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to "rec1"))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.uiState.filter { it.loadState !is PreparationScreenLoadState.Loading }.first()

        assertThat(viewModel.uiState.value.recipe).isEqualTo(recipe)
        assertThat(viewModel.uiState.value.outputIngredientName).isEqualTo("Output Name")
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
        var callCount = 0
        every { preparationRecipeRepository.observeRecipe(recipeId) } answers {
            callCount++
            if (callCount == 1) kotlinx.coroutines.flow.flow { throw RuntimeException("Fail") }
            else flowOf(createRecipe("rec1", "out1"))
        }

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
    fun `missing recipeId leads to RecipeNotFound state`() = runTest {
        viewModel = PreparationRecipeDetailViewModel(
            preparationRecipeRepository, ingredientRepository, 
            SavedStateHandle(mapOf("recipeId" to ""))
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(PreparationScreenLoadState.RecipeNotFound)
    }

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
}
