package com.miara.cuentame.feature.waste.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.WasteDetails
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.usecase.DeleteWasteDraftUseCase
import com.miara.cuentame.core.domain.usecase.ObserveWasteEventDetailsUseCase
import com.miara.cuentame.core.domain.usecase.PostWasteEventUseCase
import com.miara.cuentame.core.domain.usecase.VoidWasteEventUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.waste.WasteEvent
import io.mockk.every
import io.mockk.mockk
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
class WasteDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val wasteRepository = mockk<WasteRepository>(relaxed = true)
    private val restaurantRepository = mockk<RestaurantRepository>(relaxed = true)
    private val observeWasteEventDetailsUseCase = mockk<ObserveWasteEventDetailsUseCase>(relaxed = true)
    private val deleteWasteDraftUseCase = mockk<DeleteWasteDraftUseCase>(relaxed = true)
    private val postWasteEventUseCase = mockk<PostWasteEventUseCase>(relaxed = true)
    private val voidWasteEventUseCase = mockk<VoidWasteEventUseCase>(relaxed = true)

    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test", "USD", "en", Instant.now(), Instant.now())
    private val wasteEventId = WasteEventId("event-1")
    private val wasteEvent = WasteEvent(
        id = wasteEventId,
        restaurantId = restaurant.id,
        ingredientId = IngredientId("ing-1"),
        areaId = InventoryAreaId("area-1"),
        ingredientUnitOptionId = IngredientUnitOptionId("opt-1"),
        quantityEntered = BigDecimal("5.0"),
        quantityBase = BigDecimal("5.0"),
        reason = WasteReason.SPOILED,
        effectiveAt = Instant.now(),
        notes = null,
        attachmentPath = null,
        status = DocumentStatus.DRAFT,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        postedAt = null,
        voidedAt = null
    )
    private val wasteDetails = WasteDetails(
        event = wasteEvent,
        ingredientName = "Chicken",
        areaName = "Kitchen",
        unitLabel = "lb",
        baseUnitSymbol = "lb",
        currentAreaQuantityBase = BigDecimal("50.0"),
        remainingAreaQuantityBase = BigDecimal("45.0"),
        averageCostBase = BigDecimal("2.0"),
        estimatedValue = BigDecimal("10.0"),
        createsNegativeBalance = false
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(id: String? = wasteEventId.value): WasteDetailViewModel {
        return WasteDetailViewModel(
            SavedStateHandle(mapOf("wasteEventId" to id)),
            wasteRepository,
            restaurantRepository,
            observeWasteEventDetailsUseCase,
            deleteWasteDraftUseCase,
            postWasteEventUseCase,
            voidWasteEventUseCase
        )
    }

    @Test
    fun `loading to ready state`() = runTest {
        every { observeWasteEventDetailsUseCase(wasteEventId) } returns flowOf(wasteDetails)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.Loading)
            val state = awaitItem().screenState
            assertThat(state).isInstanceOf(WasteDetailScreenState.Ready::class.java)
            assertThat((state as WasteDetailScreenState.Ready).details).isEqualTo(wasteDetails)
        }
    }

    @Test
    fun `not found state`() = runTest {
        every { observeWasteEventDetailsUseCase(wasteEventId) } returns flowOf(null)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.Loading)
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.NotFound)
        }
    }

    @Test
    fun `invalid route state`() = runTest {
        val viewModel = createViewModel(null)
        viewModel.uiState.test {
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.Loading)
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.InvalidRoute)
        }
    }

    @Test
    fun `ownership mismatch state`() = runTest {
        val otherWaste = wasteDetails.copy(event = wasteEvent.copy(restaurantId = RestaurantId("other")))
        every { observeWasteEventDetailsUseCase(wasteEventId) } returns flowOf(otherWaste)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.Loading)
            assertThat(awaitItem().screenState).isEqualTo(WasteDetailScreenState.OwnershipMismatch)
        }
    }
}
