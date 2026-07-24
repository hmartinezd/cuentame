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
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
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
        val lookupAStarted = CompletableDeferred<Unit>()
        
        coEvery { ingredientRepository.getUnitOptions(ingA, true) } coAnswers { 
            lookupAStarted.complete(Unit)
            deferredA.await() 
        }
        coEvery { ingredientRepository.getUnitOptions(ingB, true) } returns listOf(optB)

        val viewModel = createViewModel()
        viewModel.uiState.test {
            // Initial state (Loading)
            assertThat(awaitItem().screenState).isEqualTo(WasteFormScreenState.Loading)
            // Transition to Ready
            var state = awaitItem()
            while (state.screenState != WasteFormScreenState.Ready) {
                state = awaitItem()
            }

            // 1. Select A
            viewModel.onIngredientSelected(ingA)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(lookupAStarted.isCompleted).isTrue()

            // 2. Select B while A is suspended
            viewModel.onIngredientSelected(ingB)
            testDispatcher.scheduler.advanceUntilIdle()
            
            // B should complete and be in state
            // We expect some emissions as flows combine
            state = awaitItem()
            while (state.selectedIngredientId != ingB || state.selectedUnitOptionId == null) {
                state = awaitItem()
            }
            assertThat(state.selectedIngredientId).isEqualTo(ingB)
            assertThat(state.selectedUnitOptionId).isEqualTo(optB.id)

            // 3. Complete A afterward
            deferredA.complete(listOf(optA))
            testDispatcher.scheduler.advanceUntilIdle()

            // Final state must still be B. No new items should have been produced that change it to A.
            expectNoEvents()
            val finalState = viewModel.uiState.value
            assertThat(finalState.selectedIngredientId).isEqualTo(ingB)
            assertThat(finalState.selectedUnitOptionId).isEqualTo(optB.id)
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

        val deferred1 = CompletableDeferred<WastePreview>()
        val lookup1Started = CompletableDeferred<Unit>()
        val preview2 = mockk<WastePreview>()

        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("1.0"), any()) } coAnswers { 
            lookup1Started.complete(Unit)
            deferred1.await() 
        }
        coEvery { previewWasteUseCase(restId, ingId, areaId, optId, BigDecimal("2.0"), any()) } returns preview2

        val viewModel = createViewModel()
        viewModel.uiState.test {
            // Wait for Ready
            var state = awaitItem()
            while (state.screenState != WasteFormScreenState.Ready) {
                state = awaitItem()
            }
            
            viewModel.onIngredientSelected(ingId)
            viewModel.onAreaSelected(areaId)
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Consume states until IDs are set
            state = awaitItem()
            while (state.selectedIngredientId == null || state.selectedAreaId == null) {
                state = awaitItem()
            }
            
            // Start Preview 1
            viewModel.onQuantityChanged("1.0")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(lookup1Started.isCompleted).isTrue()
            
            // Start Preview 2 while 1 is suspended
            viewModel.onQuantityChanged("2.0")
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Complete 1 afterward
            deferred1.complete(mockk())
            testDispatcher.scheduler.advanceUntilIdle()
            
            val finalState = expectMostRecentItem()
            assertThat(finalState.preview).isEqualTo(preview2)
            assertThat(finalState.isLoadingPreview).isFalse()
            assertThat(finalState.error).isNull()
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
