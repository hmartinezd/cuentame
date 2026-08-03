package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchDetailViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
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

    private fun createViewModel(batchId: String = "batch1"): ProductionBatchDetailViewModel {
        return ProductionBatchDetailViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            SavedStateHandle(mapOf("batchId" to batchId))
        )
    }

    @Test
    fun `data enrichment and initialization success`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1"); every { currencyCode } returns "USD" }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.POSTED
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(IngredientId("ing1")) } returns mockk { every { name } returns "Output Ing" }
        coEvery { inventoryAreaRepository.getById(InventoryAreaId("area1")) } returns mockk { every { name } returns "Output Area" }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("ing1"), true) } returns listOf(
            mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Output Unit" }
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals("Output Ing", state.outputIngredientName)
        assertEquals("Output Area", state.outputAreaName)
        assertEquals("Output Unit", state.outputUnitLabel)
        assertEquals("USD", state.currencyCode)
    }

    @Test
    fun `navigation to draft if status is DRAFT`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)

        val viewModel = createViewModel()
        
        val events = mutableListOf<ProductionBatchDetailEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        
        advanceUntilIdle()
        
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchDetailEvent.NavigateToDraft)
        
        job.cancel()
    }

    @Test
    fun `void success`() = runTest {
        // Setup initial data
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1"); every { currencyCode } returns "USD" }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.POSTED
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing" }
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { name } returns "Area" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit" })

        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { productionBatchRepository.void(ProductionBatchId("batch1")) } returns Unit
        
        viewModel.onVoid()
        advanceUntilIdle()
        
        coVerify { productionBatchRepository.void(ProductionBatchId("batch1")) }
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException on void`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1"); every { currencyCode } returns "USD" }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(mockk {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.POSTED
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
        })
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing" }
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { name } returns "Area" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { productionBatchRepository.void(any()) } throws CancellationException()
        
        viewModel.onVoid()
        advanceUntilIdle()
    }
}
