package com.miara.cuentame.feature.preparations.viewmodel

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.PreparationRecipeRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PreparationRecipeListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preparationRecipeRepository = mockk<PreparationRecipeRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    
    private val restaurantId = RestaurantId("r1")
    private val restaurant = Restaurant(restaurantId, "Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

    private lateinit var viewModel: PreparationRecipeListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads recipes from repository`() = runTest {
        val recipes = listOf(
            createSummary("rec1", "Recipe 1", "Ing 1", PreparationRecipeStatus.ACTIVE),
            createSummary("rec2", "Recipe 2", "Ing 2", PreparationRecipeStatus.DRAFT)
        )
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(recipes)

        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipes).isEqualTo(recipes)
    }

    @Test
    fun `search filters by recipe name`() = runTest {
        val recipes = listOf(
            createSummary("rec1", "Onion Soup", "Onion", PreparationRecipeStatus.ACTIVE),
            createSummary("rec2", "Tomato Sauce", "Tomato", PreparationRecipeStatus.ACTIVE)
        )
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(recipes)

        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("onion")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipes).hasSize(1)
        assertThat(viewModel.uiState.value.recipes[0].recipeName).isEqualTo("Onion Soup")
    }

    @Test
    fun `search filters by output ingredient name`() = runTest {
        val recipes = listOf(
            createSummary("rec1", "Base", "Chicken", PreparationRecipeStatus.ACTIVE),
            createSummary("rec2", "Base", "Beef", PreparationRecipeStatus.ACTIVE)
        )
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(recipes)

        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("beef")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipes).hasSize(1)
        assertThat(viewModel.uiState.value.recipes[0].outputIngredientName).isEqualTo("Beef")
    }

    @Test
    fun `status filter limits recipes`() = runTest {
        val recipes = listOf(
            createSummary("rec1", "R1", "I1", PreparationRecipeStatus.ACTIVE),
            createSummary("rec2", "R2", "I2", PreparationRecipeStatus.DRAFT)
        )
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(recipes)

        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onStatusFilterChanged(PreparationRecipeStatus.DRAFT)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recipes).hasSize(1)
        assertThat(viewModel.uiState.value.recipes[0].status).isEqualTo(PreparationRecipeStatus.DRAFT)
    }

    @Test
    fun `status filter to ARCHIVED automatically enables includeArchived`() = runTest {
        every { preparationRecipeRepository.observeRecipes(any(), any()) } returns flowOf(emptyList())
        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onStatusFilterChanged(PreparationRecipeStatus.ARCHIVED)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.includeArchived).isTrue()
        io.mockk.verify { preparationRecipeRepository.observeRecipes(restaurantId, true) }
    }

    @Test
    fun `onRetry increments retry trigger and refreshes observation`() = runTest {
        every { preparationRecipeRepository.observeRecipes(restaurantId, false) } returns flowOf(emptyList())

        viewModel = PreparationRecipeListViewModel(preparationRecipeRepository, restaurantRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onRetry()
        advanceUntilIdle()

        // Incremented trigger should cause flatMapLatest to re-subscribe
        io.mockk.verify(exactly = 2) { preparationRecipeRepository.observeRecipes(restaurantId, false) }
    }

    private fun createSummary(id: String, name: String, ingName: String, status: PreparationRecipeStatus) = PreparationRecipeSummary(
        id = PreparationRecipeId(id),
        outputIngredientId = com.miara.cuentame.core.common.ids.IngredientId("i-$id"),
        outputIngredientName = ingName,
        recipeName = name,
        status = status,
        standardYieldQuantity = java.math.BigDecimal.ONE,
        yieldUnitLabel = "Unit",
        componentCount = 0,
        updatedAt = Instant.now()
    )
}
