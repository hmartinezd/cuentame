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
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
class WasteFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val wasteRepository = mockk<WasteRepository>(relaxed = true)
    private val ingredientRepository = mockk<IngredientRepository>(relaxed = true)
    private val areaRepository = mockk<InventoryAreaRepository>(relaxed = true)
    private val restaurantRepository = mockk<RestaurantRepository>(relaxed = true)
    private val createWasteDraftUseCase = mockk<CreateWasteDraftUseCase>(relaxed = true)
    private val updateWasteDraftUseCase = mockk<UpdateWasteDraftUseCase>(relaxed = true)
    private val previewWasteUseCase = mockk<PreviewWasteUseCase>(relaxed = true)
    private val attachmentPermissionManager = mockk<LocalAttachmentPermissionManager>(relaxed = true)

    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test Rest", "USD", "en", Instant.now(), Instant.now())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } answers { mockk(relaxed = true) }

        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { areaRepository.observeAllAreas() } returns flowOf(emptyList())
        every { attachmentPermissionManager.persistReadPermission(any()) } returns Result.success(Unit)
    }



    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Ready`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().screenState).isEqualTo(WasteFormScreenState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            var item = awaitItem()
            while (item.screenState == WasteFormScreenState.Loading) { item = awaitItem() }
            assertThat(item.screenState).isEqualTo(WasteFormScreenState.Ready)
        }
    }

    @Test
    fun `selecting ingredient updates unit options`() = runTest {
        val ingId = IngredientId("ing-1")
        val option = IngredientUnitOption(IngredientUnitOptionId("opt-1"), ingId, "lb", "lb", null, BigDecimal.ONE, true, true, false, true, Instant.now(), Instant.now())
        
        coEvery { ingredientRepository.getUnitOptions(any(), any()) } returns listOf(option)
        
        val viewModel = createViewModel()
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1) // Loading

            viewModel.onIngredientSelected(ingId)
            testDispatcher.scheduler.advanceUntilIdle()
            
            var state = awaitItem()
            while (state.selectedIngredientId != ingId || state.selectedUnitOptionId == null) { 
                state = awaitItem() 
            }
            assertThat(state.selectedIngredientId).isEqualTo(ingId)
            assertThat(state.selectedUnitOptionId).isEqualTo(option.id)
        }
    }



    @Test
    fun `attachment permission failure handles error and preserves existing`() = runTest {
        val uriA = "uri-A"
        val uriB = "uri-B"
        
        val viewModel = createViewModel()
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Select uriA successfully
            viewModel.onAttachmentChanged(uriA)
            testDispatcher.scheduler.advanceUntilIdle()
            var state = awaitItem()
            while (state.attachmentUri != uriA) { state = awaitItem() }
            assertThat(state.attachmentUri).isEqualTo(uriA)
            
            // Configure failure
            every { attachmentPermissionManager.persistReadPermission(android.net.Uri.parse(uriB)) } returns Result.failure(RuntimeException("Fail"))
            
            // Try to select uriB
            viewModel.onAttachmentChanged(uriB)
            testDispatcher.scheduler.advanceUntilIdle()
            
            state = awaitItem()
            while (state.error == null) { state = awaitItem() }
            assertThat(state.attachmentUri).isEqualTo(uriA)
            assertThat(state.error).isNotNull()
            
            // Clear error
            viewModel.clearError()
            testDispatcher.scheduler.advanceUntilIdle()
            state = awaitItem()
            while (state.error != null) { state = awaitItem() }
            
            // Retry uriB successfully
            every { attachmentPermissionManager.persistReadPermission(android.net.Uri.parse(uriB)) } returns Result.success(Unit)
            viewModel.onAttachmentChanged(uriB)
            testDispatcher.scheduler.advanceUntilIdle()
            
            state = awaitItem()
            while (state.attachmentUri != uriB) { state = awaitItem() }
            assertThat(state.attachmentUri).isEqualTo(uriB)
        }
    }

    @Test
    fun `preview failure handles error and clears preview`() = runTest {
        val ingId = IngredientId("ing-1")
        val areaId = InventoryAreaId("area-1")
        val optId = IngredientUnitOptionId("opt-1")
        val option = IngredientUnitOption(optId, ingId, "lb", "lb", null, BigDecimal.ONE, true, true, false, true, Instant.now(), Instant.now())
        
        coEvery { ingredientRepository.getUnitOptions(ingId, true) } returns listOf(option)
        coEvery { previewWasteUseCase(any(), any(), any(), any(), any(), any()) } throws RuntimeException("Preview fail")
        
        val viewModel = createViewModel()
        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onIngredientSelected(ingId)
            viewModel.onAreaSelected(areaId)
            viewModel.onQuantityChanged("5.0")
            
            testDispatcher.scheduler.advanceUntilIdle()
            
            var state = awaitItem()
            while (state.error == null) { state = awaitItem() }
            assertThat(state.error).isNotNull()
            assertThat(state.preview).isNull()
            
            // Retry
            coEvery { previewWasteUseCase(any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
            viewModel.onQuantityChanged("6.0")
            testDispatcher.scheduler.advanceUntilIdle()
            
            state = awaitItem()
            while (state.preview == null) { state = awaitItem() }
            assertThat(state.preview).isNotNull()
            assertThat(state.error).isNull()
        }
    }

    private fun createViewModel(wasteEventId: String? = null): WasteFormViewModel {
        return WasteFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("wasteEventId" to wasteEventId)),
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
