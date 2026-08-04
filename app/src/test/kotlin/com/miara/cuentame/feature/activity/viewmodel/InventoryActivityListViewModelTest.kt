package com.miara.cuentame.feature.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryActivityListViewModelTest {

    private val activityRepository = mockk<InventoryActivityRepository>()
    private val ingredientRepository = mockk<IngredientRepository>()
    private val areaRepository = mockk<InventoryAreaRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val restaurant = Restaurant(
        id = RestaurantId("res1"),
        name = "Test",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { restaurantRepository.observeRestaurant() } returns flowOf(restaurant)
        every { ingredientRepository.observeIngredients(any(), any()) } returns flowOf(emptyList())
        every { areaRepository.observeAllAreas() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): InventoryActivityListViewModel {
        return InventoryActivityListViewModel(
            activityRepository,
            ingredientRepository,
            areaRepository,
            restaurantRepository,
            handle
        )
    }

    @Test
    fun `initial state is Loading`() = runTest {
        every { restaurantRepository.observeRestaurant() } returns flowOf(null)
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value).isEqualTo(InventoryActivityListScreenState.Loading)
    }

    @Test
    fun `calculates summary correctly`() = runTest {
        val item1 = createItem("m1", BigDecimal("10.0"), BigDecimal("20.0")) // IN
        val item2 = createItem("m2", BigDecimal("-5.0"), BigDecimal("-10.0")) // OUT
        val items = listOf(item1, item2)

        every { activityRepository.observeActivity(any()) } returns flowOf(items)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.movementCount).isEqualTo(2)
        assertThat(state.summary.incomingMovementCount).isEqualTo(1)
        assertThat(state.summary.outgoingMovementCount).isEqualTo(1)
        assertThat(state.summary.valueAdded).isEqualTo(BigDecimal("20.0"))
        assertThat(state.summary.valueRemoved).isEqualTo(BigDecimal("10.0"))
    }

    @Test
    fun `quantity summary present only for single ingredient`() = runTest {
        val ing1 = IngredientId("ing1")
        val item1 = createItem("m1", BigDecimal("10.0"), ingredientId = ing1)
        val item2 = createItem("m2", BigDecimal("5.0"), ingredientId = ing1)
        
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(item1, item2))
        
        val viewModel = createViewModel()
        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.quantitySummary).isNotNull()
        assertThat(state.summary.quantitySummary!!.netQuantity).isEqualTo(BigDecimal("15.0"))

        // Add second ingredient
        val item3 = createItem("m3", BigDecimal("1.0"), ingredientId = IngredientId("ing2"))
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(item1, item2, item3))
        
        val state2 = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state2.summary.quantitySummary).isNull()
    }

    private fun createItem(
        id: String,
        qty: BigDecimal,
        value: BigDecimal? = null,
        ingredientId: IngredientId = IngredientId("ing1")
    ) = InventoryActivityItem(
        movement = InventoryMovement(
            id = InventoryMovementId(id),
            restaurantId = restaurant.id,
            ingredientId = ingredientId,
            areaId = InventoryAreaId("area1"),
            movementType = InventoryMovementType.PURCHASE,
            quantityBaseSigned = qty,
            unitCostBaseSnapshot = null,
            totalValueSnapshot = value,
            effectiveAt = Instant.EPOCH,
            sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
            sourceDocumentId = "doc1",
            sourceOperationId = "op1",
            sourceLineId = null,
            reversalOfMovementId = null,
            createdAt = Instant.EPOCH
        ),
        ingredientName = "Ing",
        areaName = "Area",
        baseUnitSymbol = "lb",
        sourceDisplay = InventoryActivitySourceDisplay("Title", null, null),
        reversedByMovementId = null,
        reversalOfDisplay = null,
        reversedByDisplay = null
    )
}
