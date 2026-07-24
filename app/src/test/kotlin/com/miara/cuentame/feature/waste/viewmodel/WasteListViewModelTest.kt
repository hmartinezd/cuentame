package com.miara.cuentame.feature.waste.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.WasteSummary
import com.miara.cuentame.core.domain.usecase.ObserveWasteEventsUseCase
import com.miara.cuentame.core.model.restaurant.Restaurant
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WasteListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>(relaxed = true)
    private val observeWasteEventsUseCase = mockk<ObserveWasteEventsUseCase>(relaxed = true)

    private val restaurant = Restaurant(RestaurantId("rest-1"), "Test", "USD", "en", Instant.now(), Instant.now())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows events from repository`() = runTest {
        val summaries = listOf<WasteSummary>(mockk())
        every { observeWasteEventsUseCase(any()) } returns flowOf(summaries)
        
        val viewModel = WasteListViewModel(observeWasteEventsUseCase, restaurantRepository)
        
        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.wasteEvents).isEqualTo(summaries)
            assertThat(state.currencyCode).isEqualTo("USD")
        }
    }
}
