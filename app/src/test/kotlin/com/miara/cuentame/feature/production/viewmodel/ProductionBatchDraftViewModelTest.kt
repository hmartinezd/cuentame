package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.ProductionBatchRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UpdateProductionBatchDraftCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchDraftViewModelTest {

    private val productionBatchRepository = mockk<ProductionBatchRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val inventoryAreaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val timeProvider = mockk<com.miara.cuentame.core.common.time.TimeProvider>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(batchId: String = "batch1"): ProductionBatchDraftViewModel {
        return ProductionBatchDraftViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            timeProvider,
            SavedStateHandle(mapOf("batchId" to batchId)),
        )
    }

    @Test
    fun `strict reference enrichment verified`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
            every { batchMultiplier } returns BigDecimal("1")
            every { actualOutputQuantityEntered } returns BigDecimal("10")
            every { effectiveAt } returns Instant.now()
            every { notes } returns null
            every { hasManualOutputQuantityOverride } returns false
            every { recipeStandardYieldQuantitySnapshot } returns BigDecimal("10")
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(InventoryAreaId("area1")) } returns null // Failure here

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Lookup failure enters LoadError
        assertTrue(viewModel.uiState.value.screenState is ProductionBatchScreenState.LoadError)
    }

    @Test
    fun `dirty-only patch semantics verified`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
            every { batchMultiplier } returns BigDecimal("1")
            every { actualOutputQuantityEntered } returns BigDecimal("10")
            every { effectiveAt } returns Instant.now()
            every { notes } returns null
            every { hasManualOutputQuantityOverride } returns false
            every { recipeStandardYieldQuantitySnapshot } returns BigDecimal("10")
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        coEvery { inventoryAreaRepository.getById(InventoryAreaId("area1")) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }
        coEvery { ingredientRepository.getById(IngredientId("ing1")) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("ing1"), includeArchived = true) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Change only notes
        viewModel.onNotesChanged("Updated notes")
        
        coEvery { productionBatchRepository.updateDraft(any()) } returns Unit
        
        viewModel.onSave()
        advanceUntilIdle()

        val commandSlot = slot<UpdateProductionBatchDraftCommand>()
        coVerify { productionBatchRepository.updateDraft(capture(commandSlot)) }
        
        val command = commandSlot.captured
        assertEquals("Updated notes", command.notes)
        assertNull(command.batchMultiplier) // Not dirty
        assertNull(command.outputAreaId) // Not dirty
        assertNull(command.actualOutputQuantityEntered) // Not dirty
    }

    @Test
    fun `unsaved changes guard on review button`() = runTest {
        // Setup initial data (Ready state)
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
            every { batchMultiplier } returns BigDecimal("1")
            every { actualOutputQuantityEntered } returns BigDecimal("10")
            every { effectiveAt } returns Instant.now()
            every { notes } returns null
            every { hasManualOutputQuantityOverride } returns false
            every { recipeStandardYieldQuantitySnapshot } returns BigDecimal("10")
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Make it dirty
        viewModel.onNotesChanged("Dirty")
        
        val events = mutableListOf<ProductionBatchDraftEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        
        viewModel.onReview()
        advanceUntilIdle()
        
        assertTrue(events.isEmpty()) // Blocked by unsaved changes

        // Save
        coEvery { productionBatchRepository.updateDraft(any()) } returns Unit
        viewModel.onSave()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        
        viewModel.onReview()
        advanceUntilIdle()
        
        assertEquals(1, events.size)
        assertTrue(events[0] is ProductionBatchDraftEvent.NavigateToPreview)
        
        job.cancel()
    }

    @Test
    fun `serialization of operations - isSaving prevents onSave`() = runTest {
        // Setup initial data
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
            every { batchMultiplier } returns BigDecimal("1")
            every { actualOutputQuantityEntered } returns BigDecimal("10")
            every { effectiveAt } returns Instant.now()
            every { notes } returns null
            every { hasManualOutputQuantityOverride } returns false
            every { recipeStandardYieldQuantitySnapshot } returns BigDecimal("10")
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(listOf(mockk { every { id } returns InventoryAreaId("area1") }))
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNotesChanged("Test")
        
        // Mock a slow save
        coEvery { productionBatchRepository.updateDraft(any()) } coAnswers {
            kotlinx.coroutines.delay(java.time.Duration.ofSeconds(1).toMillis())
        }
        
        launch { viewModel.onSave() }
        advanceTimeBy(500)
        assertTrue(viewModel.uiState.value.isSaving)
        
        // Second call should be ignored
        viewModel.onSave()
        
        advanceUntilIdle()
        coVerify(exactly = 1) { productionBatchRepository.updateDraft(any()) }
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException on save`() = runTest {
        // Setup similar to above
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { outputAreaId } returns InventoryAreaId("area1")
            every { outputIngredientId } returns IngredientId("ing1")
            every { outputUnitOptionId } returns IngredientUnitOptionId("unit1")
            every { components } returns emptyList()
            every { batchMultiplier } returns BigDecimal("1")
            every { actualOutputQuantityEntered } returns BigDecimal("10")
            every { effectiveAt } returns Instant.now()
            every { notes } returns null
            every { hasManualOutputQuantityOverride } returns false
            every { recipeStandardYieldQuantitySnapshot } returns BigDecimal("10")
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })

        val viewModel = createViewModel()
        advanceUntilIdle()
        
        viewModel.onNotesChanged("Test")
        coEvery { productionBatchRepository.updateDraft(any()) } throws CancellationException()
        
        viewModel.onSave()
        advanceUntilIdle()
    }
}
