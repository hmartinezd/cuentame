package com.miara.cuentame.feature.production.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.core.model.restaurant.Restaurant
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBatchComponentViewModelTest {

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

    private fun createViewModel(batchId: String = "batch1", componentId: String = "comp1"): ProductionBatchComponentViewModel {
        return ProductionBatchComponentViewModel(
            productionBatchRepository,
            ingredientRepository,
            inventoryAreaRepository,
            restaurantRepository,
            SavedStateHandle(mapOf("batchId" to batchId, "componentId" to componentId))
        )
    }

    @Test
    fun `data enrichment and initialization success`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val component = mockk<ProductionBatchComponent> {
            every { id } returns ProductionBatchComponentId("comp1")
            every { componentIngredientId } returns IngredientId("ing1")
            every { unitOptionId } returns IngredientUnitOptionId("unit1")
            every { recipeUnitOptionIdSnapshot } returns IngredientUnitOptionId("unit1")
            every { sourceAreaId } returns InventoryAreaId("area1")
            every { actualQuantityEntered } returns BigDecimal("5")
            every { notes } returns "Initial notes"
            every { hasManualQuantityOverride } returns false
        }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { components } returns listOf(component)
        }

        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(ProductionBatchId("batch1")) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(IngredientId("ing1")) } returns mockk { every { name } returns "Ingredient 1" }
        coEvery { ingredientRepository.getUnitOptions(IngredientId("ing1"), true) } returns listOf(
            mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" }
        )
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(InventoryAreaId("area1")) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProductionBatchScreenState.Ready, state.screenState)
        assertEquals("Ingredient 1", state.ingredientName)
        assertEquals(BigDecimal("5").toPlainString(), state.actualQuantity)
        assertEquals("Initial notes", state.notes)
    }

    @Test
    fun `dirty-only patch semantics verified for component save`() = runTest {
        // Setup initial data (Ready state)
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val component = mockk<ProductionBatchComponent> {
            every { id } returns ProductionBatchComponentId("comp1")
            every { componentIngredientId } returns IngredientId("ing1")
            every { unitOptionId } returns IngredientUnitOptionId("unit1")
            every { recipeUnitOptionIdSnapshot } returns IngredientUnitOptionId("unit1")
            every { sourceAreaId } returns InventoryAreaId("area1")
            every { actualQuantityEntered } returns BigDecimal("5")
            every { notes } returns null
            every { hasManualQuantityOverride } returns false
        }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { components } returns listOf(component)
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Change only notes
        viewModel.onNotesChanged("Component notes")
        
        coEvery { productionBatchRepository.updateComponent(any()) } returns Unit
        
        viewModel.onSave()
        advanceUntilIdle()

        val commandSlot = slot<UpdateProductionBatchComponentCommand>()
        coVerify { productionBatchRepository.updateComponent(capture(commandSlot)) }
        
        val command = commandSlot.captured
        assertEquals("Component notes", command.notes)
        assertNull(command.actualQuantityEntered) // Not dirty
        assertNull(command.sourceAreaId) // Not dirty
    }

    @Test
    fun `form state synchronization after component reset`() = runTest {
        // Setup initial data (Ready state)
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        val component = mockk<ProductionBatchComponent> {
            every { id } returns ProductionBatchComponentId("comp1")
            every { componentIngredientId } returns IngredientId("ing1")
            every { unitOptionId } returns IngredientUnitOptionId("unit1")
            every { recipeUnitOptionIdSnapshot } returns IngredientUnitOptionId("unit1")
            every { sourceAreaId } returns InventoryAreaId("area1")
            every { actualQuantityEntered } returns BigDecimal("5")
            every { notes } returns null
            every { hasManualQuantityOverride } returns false
        }
        val batch = mockk<ProductionBatch> {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { components } returns listOf(component)
        }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(batch)
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing 1" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(mockk { every { id } returns IngredientUnitOptionId("unit1"); every { displayName } returns "Unit 1" })
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())
        coEvery { inventoryAreaRepository.getById(any()) } returns mockk { every { id } returns InventoryAreaId("area1"); every { name } returns "Area 1" }

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Change quantity manually
        viewModel.onQuantityChanged("10")
        assertTrue(viewModel.uiState.value.quantityDirty)

        // Reset to expected
        val resetComponent = mockk<ProductionBatchComponent> {
            every { id } returns ProductionBatchComponentId("comp1")
            every { actualQuantityEntered } returns BigDecimal("5") // The original expected
            every { unitOptionId } returns IngredientUnitOptionId("unit1")
        }
        val resetBatch = mockk<ProductionBatch> {
            every { components } returns listOf(resetComponent)
        }
        coEvery { productionBatchRepository.resetComponentToExpected(ProductionBatchId("batch1"), ProductionBatchComponentId("comp1")) } returns Unit
        coEvery { productionBatchRepository.getBatch(ProductionBatchId("batch1")) } returns resetBatch

        viewModel.onResetToRecipe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BigDecimal("5").toPlainString(), state.actualQuantity)
        assertFalse(state.quantityDirty)
        assertFalse(state.hasManualOverride)
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException on reset`() = runTest {
        val restaurant = mockk<Restaurant> { every { id } returns RestaurantId("res1") }
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { productionBatchRepository.observeBatch(any()) } returns flowOf(mockk {
            every { id } returns ProductionBatchId("batch1")
            every { status } returns DocumentStatus.DRAFT
            every { components } returns listOf(mockk { 
                every { id } returns ProductionBatchComponentId("comp1") 
                every { componentIngredientId } returns IngredientId("ing1")
                every { unitOptionId } returns IngredientUnitOptionId("unit1")
                every { recipeUnitOptionIdSnapshot } returns IngredientUnitOptionId("unit1")
                every { sourceAreaId } returns null
                every { actualQuantityEntered } returns BigDecimal("5")
                every { notes } returns null
                every { hasManualQuantityOverride } returns false
            })
        })
        coEvery { ingredientRepository.getById(any()) } returns mockk { every { name } returns "Ing" }
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns emptyList()
        every { inventoryAreaRepository.observeActiveAreas() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { productionBatchRepository.resetComponentToExpected(any(), any()) } throws CancellationException()
        
        viewModel.onResetToRecipe()
        advanceUntilIdle()
    }
}
