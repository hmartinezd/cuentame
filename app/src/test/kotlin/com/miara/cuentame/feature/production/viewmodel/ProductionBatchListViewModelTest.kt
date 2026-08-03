package com.miara.cuentame.feature.production.viewmodel

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.ProductionBatchRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatchSummary
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchListViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ProductionBatchListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null
        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        
        assertEquals(ProductionBatchScreenState.Loading, viewModel.uiState.value.screenState)
    }

    @Test
    fun `enters LoadError when restaurant lookup fails`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null
        
        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.screenState
        assertTrue(state is ProductionBatchScreenState.LoadError)
    }

    @Test
    fun `loads batches successfully`() = runTest {
        val restaurantId = RestaurantId("res1")
        val restaurant = mockk<Restaurant> { every { id } returns restaurantId }
        val batches = listOf(
            mockk<ProductionBatchSummary> {
                every { recipeName } returns "Recipe A"
                every { outputIngredientName } returns "Ing A"
            }
        )
        
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatches(restaurantId, null) } returns flowOf(batches)

        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()

        assertEquals(ProductionBatchScreenState.Ready, viewModel.uiState.value.screenState)
        assertEquals(batches, viewModel.uiState.value.batches)
    }

    @Test
    fun `filters batches by search query`() = runTest {
        val restaurantId = RestaurantId("res1")
        val restaurant = mockk<Restaurant> { every { id } returns restaurantId }
        val batch1 = mockk<ProductionBatchSummary> {
            every { recipeName } returns "Apple Pie"
            every { outputIngredientName } returns "Pie"
        }
        val batch2 = mockk<ProductionBatchSummary> {
            every { recipeName } returns "Banana Bread"
            every { outputIngredientName } returns "Bread"
        }
        
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatches(restaurantId, null) } returns flowOf(listOf(batch1, batch2))

        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Apple")
        advanceUntilIdle()

        assertEquals(listOf(batch1), viewModel.uiState.value.batches)
    }

    @Test
    fun `filters batches by status`() = runTest {
        val restaurantId = RestaurantId("res1")
        val restaurant = mockk<Restaurant> { every { id } returns restaurantId }
        
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatches(restaurantId, DocumentStatus.POSTED) } returns flowOf(emptyList())
        every { productionBatchRepository.observeBatches(restaurantId, null) } returns flowOf(emptyList())

        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()

        viewModel.onStatusFilterChanged(DocumentStatus.POSTED)
        advanceUntilIdle()

        verify { productionBatchRepository.observeBatches(restaurantId, DocumentStatus.POSTED) }
    }

    @Test
    fun `retry reloads data`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null
        
        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.screenState is ProductionBatchScreenState.LoadError)

        val restaurantId = RestaurantId("res1")
        val restaurant = mockk<Restaurant> { every { id } returns restaurantId }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatches(restaurantId, null) } returns flowOf(emptyList())

        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(ProductionBatchScreenState.Ready, viewModel.uiState.value.screenState)
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws CancellationException()
        
        viewModel = ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
        advanceUntilIdle()
    }
}
