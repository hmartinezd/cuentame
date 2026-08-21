package com.venkoi.restaurantops.feature.production.viewmodel

import com.venkoi.restaurantops.core.common.ids.ProductionBatchId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.ProductionBatchRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.ProductionBatchSummary
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchListViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val restaurant = Restaurant(
        id = RestaurantId("res1"),
        name = "Test Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProductionBatchListViewModel {
        return ProductionBatchListViewModel(productionBatchRepository, restaurantRepository)
    }

    private fun createBatchSummary(
        id: String,
        recipeName: String = "Recipe",
        outputIngredientName: String = "Ingredient",
        status: DocumentStatus = DocumentStatus.DRAFT
    ) = ProductionBatchSummary(
        id = ProductionBatchId(id),
        recipeName = recipeName,
        outputIngredientName = outputIngredientName,
        status = status,
        expectedOutputQuantityEntered = BigDecimal.TEN,
        actualOutputQuantityEntered = BigDecimal.ZERO,
        outputUnitLabel = "kg",
        componentCount = 5,
        totalComponentCost = BigDecimal.valueOf(100),
        effectiveAt = Instant.EPOCH
    )

    @Test
    fun `loads batches successfully`() = runTest {
        val batches = listOf(createBatchSummary("batch1"))
        every { productionBatchRepository.observeBatches(restaurant.id, null) } returns flowOf(batches)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals(batches, state.batches)
        assertEquals("USD", state.currencyCode)
    }

    @Test
    fun `filters batches by search query`() = runTest {
        val batch1 = createBatchSummary("batch1", recipeName = "Apple Pie")
        val batch2 = createBatchSummary("batch2", recipeName = "Banana Bread")
        val batches = listOf(batch1, batch2)

        every { productionBatchRepository.observeBatches(restaurant.id, null) } returns flowOf(batches)

        val viewModel = createViewModel()

        viewModel.onSearchQueryChanged("apple")

        assertEquals(listOf(batch1), viewModel.uiState.value.batches)
    }

    @Test
    fun `filters batches by status`() = runTest {
        every { productionBatchRepository.observeBatches(any(), any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.onStatusFilterChanged(DocumentStatus.POSTED)

        verify { productionBatchRepository.observeBatches(restaurant.id, DocumentStatus.POSTED) }
    }

    @Test
    fun `retry reloads data`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns null

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.screenState is ProductionBatchScreenState.LoadError)

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatches(any(), any()) } returns flowOf(emptyList())
        
        viewModel.onRetry()

        assertEquals(ProductionBatchScreenState.Ready, viewModel.uiState.value.screenState)
    }

    @Test
    fun `resets loading on CancellationException`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws CancellationException()

        val viewModel = createViewModel()
        
        assertEquals(ProductionBatchScreenState.Loading, viewModel.uiState.value.screenState)
    }
}
