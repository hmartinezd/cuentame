package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchPostingPreviewViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(batchId: String = "batch1"): ProductionBatchPostingPreviewViewModel {
        return ProductionBatchPostingPreviewViewModel(
            productionBatchRepository,
            restaurantRepository,
            SavedStateHandle(mapOf("batchId" to batchId))
        )
    }

    @Test
    fun `preview calculation success`() = runTest {
        val restaurant = mockk<Restaurant> { 
            every { id } returns RestaurantId("res1")
            every { currencyCode } returns "USD"
        }
        val batch = mockk<ProductionBatch> { every { id } returns ProductionBatchId("batch1") }
        val preview = mockk<ProductionBatchPostingPreview> {
            every { blockers } returns emptyList()
            every { components } returns listOf(
                mockk { 
                    every { createsNegativeBalance } returns false
                    every { costUnavailable } returns false
                }
            )
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        coEvery { productionBatchRepository.getBatch(ProductionBatchId("batch1")) } returns batch
        coEvery { productionBatchRepository.calculatePostingPreview(ProductionBatchId("batch1")) } returns preview

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals(preview, state.preview)
        assertEquals("USD", state.currencyCode)
        assertFalse(state.hasNegativeBalances)
        assertFalse(state.hasUnavailableCosts)
    }

    @Test
    fun `detects blockers and warnings`() = runTest {
        val restaurant = mockk<Restaurant> { 
            every { id } returns RestaurantId("res1")
            every { currencyCode } returns "USD"
        }
        val batch = mockk<ProductionBatch> { every { id } returns ProductionBatchId("batch1") }
        val preview = mockk<ProductionBatchPostingPreview> {
            every { blockers } returns listOf(PostingBlocker.RECIPE_NOT_ACTIVE)
            every { components } returns listOf(
                mockk { 
                    every { createsNegativeBalance } returns true
                    every { costUnavailable } returns true
                }
            )
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        coEvery { productionBatchRepository.getBatch(any()) } returns batch
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns preview

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.blockers.isNotEmpty())
        assertTrue(state.hasNegativeBalances)
        assertTrue(state.hasUnavailableCosts)
    }

    @Test
    fun `posting success emits event`() = runTest {
        val restaurant = mockk<Restaurant> { 
            every { id } returns RestaurantId("res1")
            every { currencyCode } returns "USD"
        }
        val preview = mockk<ProductionBatchPostingPreview> {
            every { blockers } returns emptyList()
            every { components } returns emptyList()
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        coEvery { productionBatchRepository.getBatch(any()) } returns mockk()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns preview

        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { productionBatchRepository.post(ProductionBatchId("batch1")) } returns Unit
        
        val events = mutableListOf<ProductionBatchPreviewEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        
        viewModel.onPost()
        advanceUntilIdle()
        
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchPreviewEvent.Posted)
        
        job.cancel()
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException on post`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1"); every { currencyCode } returns "USD" }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        coEvery { productionBatchRepository.getBatch(any()) } returns mockk()
        coEvery { productionBatchRepository.calculatePostingPreview(any()) } returns mockk {
            every { blockers } returns emptyList()
            every { components } returns emptyList()
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { productionBatchRepository.post(any()) } throws CancellationException()
        
        viewModel.onPost()
        advanceUntilIdle()
    }
}
