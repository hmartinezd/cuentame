package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.attachment.LocalAttachmentPermissionManager
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.usecase.CreateWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.PreviewWasteUseCase
import com.miara.cuentame.core.domain.usecase.UpdateWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.WastePreview
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WasteRaceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val wasteRepository = mockk<WasteRepository>(relaxed = true)
    private val ingredientRepository = mockk<IngredientRepository>(relaxed = true)
    private val areaRepository = mockk<InventoryAreaRepository>(relaxed = true)
    private val restaurantRepository = mockk<RestaurantRepository>(relaxed = true)
    private val createWasteDraftUseCase = mockk<CreateWasteDraftUseCase>(relaxed = true)
    private val updateWasteDraftUseCase = mockk<UpdateWasteDraftUseCase>(relaxed = true)
    private val previewWasteUseCase = mockk<PreviewWasteUseCase>(relaxed = true)
    private val attachmentPermissionManager = mockk<LocalAttachmentPermissionManager>(relaxed = true)

    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test", "USD", "en", Instant.now(), Instant.now())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { areaRepository.observeAllAreas() } returns flowOf(emptyList())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ingredientRace_lastSelectionWins() = runTest {
        val ingA = IngredientId("ing-A")
        val ingB = IngredientId("ing-B")
        val optA = IngredientUnitOption(IngredientUnitOptionId("opt-A"), ingA, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, Instant.now(), Instant.now())
        val optB = IngredientUnitOption(IngredientUnitOptionId("opt-B"), ingB, "kg", "kg", null, BigDecimal.ONE, true, true, true, true, Instant.now(), Instant.now())

        val deferredA = CompletableDeferred<List<IngredientUnitOption>>()
        
        coEvery { ingredientRepository.getUnitOptions(ingA, true) } coAnswers { deferredA.await() }
        coEvery { ingredientRepository.getUnitOptions(ingB, true) } returns listOf(optB)

        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(2) // Loading, Ready

            viewModel.onIngredientSelected(ingA)
            viewModel.onIngredientSelected(ingB)
            
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Complete A lookup later
            deferredA.complete(listOf(optA))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.selectedIngredientId).isEqualTo(ingB)
            assertThat(state.selectedUnitOptionId).isEqualTo(optB.id)
            // Units should belong to B
            assertThat(state.unitOptions.all { it.id == optB.id }).isTrue()
        }
    }

    @Test
    fun previewRace_lastRequestWins() = runTest {
        val restId = restaurant.id
        val ingId = IngredientId("ing-1")
        val areaId = InventoryAreaId("area-1")
        val optId = IngredientUnitOptionId("opt-1")
        val option = IngredientUnitOption(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, Instant.now(), Instant.now())

        coEvery { ingredientRepository.getUnitOptions(ingId, true) } returns listOf(option)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val deferred1 = CompletableDeferred<WastePreview>()
        val preview2 = mockk<WastePreview>()

        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("1.0"), any()) } coAnswers { deferred1.await() }
        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("2.0"), any()) } returns preview2

        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(2) // Loading, Ready
            
            viewModel.onIngredientSelected(ingId)
            viewModel.onAreaSelected(areaId)
            testDispatcher.scheduler.advanceUntilIdle()
            
            viewModel.onQuantityChanged("1.0")
            viewModel.onQuantityChanged("2.0")
            
            testDispatcher.scheduler.advanceUntilIdle()
            
            deferred1.complete(mockk())
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertThat(state.preview).isEqualTo(preview2)
            assertThat(state.isLoadingPreview).isFalse()
        }
    }

    @Test
    fun invalidDecimal_clearsPreview() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(2)
            
            viewModel.onQuantityChanged("invalid")
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertThat(state.preview).isNull()
            assertThat(state.isLoadingPreview).isFalse()
        }
    }

    @Test
    fun scientificNotation_clearsPreview() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(2)
            
            viewModel.onQuantityChanged("1e10")
            testDispatcher.scheduler.advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertThat(state.preview).isNull()
        }
    }

    @Test
    fun stalePreviewFailure_ignored() = runTest {
        val restId = restaurant.id
        val ingId = IngredientId("ing-1")
        val areaId = InventoryAreaId("area-1")
        val optId = IngredientUnitOptionId("opt-1")
        val option = IngredientUnitOption(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, Instant.now(), Instant.now())

        coEvery { ingredientRepository.getUnitOptions(ingId, true) } returns listOf(option)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val deferred1 = CompletableDeferred<WastePreview>()
        val preview2 = mockk<WastePreview>()

        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("1.0"), any()) } coAnswers { deferred1.await() }
        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("2.0"), any()) } returns preview2

        val viewModel = createViewModel()
        viewModel.uiState.test {
            skipItems(2)
            viewModel.onIngredientSelected(ingId)
            viewModel.onAreaSelected(areaId)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onQuantityChanged("1.0")
            viewModel.onQuantityChanged("2.0")
            testDispatcher.scheduler.advanceUntilIdle()

            // Fail request 1
            deferred1.completeExceptionally(RuntimeException("stale"))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.preview).isEqualTo(preview2)
            assertThat(state.error).isNull()
        }
    }

    private fun createViewModel(): WasteFormViewModel {
        return WasteFormViewModel(
            savedStateHandle = SavedStateHandle(),
            wasteRepository = wasteRepository,
            ingredientRepository = ingredientRepository,
            areaRepository = areaRepository,
            restaurantRepository = restaurantRepository,
            createWasteDraftUseCase = createWasteDraftUseCase,
            updateWasteDraftUseCase = updateWasteDraftUseCase,
            previewWasteUseCase = previewWasteUseCase,
            attachmentPermissionManager = attachmentPermissionManager
        )
    }
}
