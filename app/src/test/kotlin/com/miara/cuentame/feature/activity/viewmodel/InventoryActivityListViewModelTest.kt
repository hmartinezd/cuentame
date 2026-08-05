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
import com.miara.cuentame.feature.activity.logic.InventoryActivityTextResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val textResolver = SimpleInventoryActivityTextResolver()
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
        every { activityRepository.observeActivity(any()) } returns flowOf(emptyList())
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
            textResolver,
            handle
        )
    }

    @Test
    fun `initial state is Loading`() = runTest {
        every { restaurantRepository.observeRestaurant() } returns flowOf(null)
        val viewModel = createViewModel()
        // No collector started yet, so it should be initial Loading
        assertThat(viewModel.uiState.value).isEqualTo(InventoryActivityListScreenState.Loading)
    }

    @Test
    fun `calculates summary correctly`() = runTest {
        val item1 = createItem("m1", BigDecimal("10.0"), BigDecimal("20.0")) // IN
        val item2 = createItem("m2", BigDecimal("-5.0"), BigDecimal("-10.0")) // OUT
        val items = listOf(item1, item2)

        every { activityRepository.observeActivity(any()) } returns flowOf(items)

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.movementCount).isEqualTo(2)
        assertThat(state.summary.incomingMovementCount).isEqualTo(1)
        assertThat(state.summary.outgoingMovementCount).isEqualTo(1)
        assertThat(state.summary.valueAdded).isEqualTo(BigDecimal("20.0"))
        assertThat(state.summary.valueRemoved).isEqualTo(BigDecimal("10.0"))
        assertThat(state.summary.valueCoverage).isEqualTo(InventoryActivityValueCoverage.COMPLETE)
    }

    @Test
    fun `quantity summary present only for single ingredient and unit`() = runTest {
        val ing1 = IngredientId("ing1")
        val item1 = createItem("m1", BigDecimal("10.0"), ingredientId = ing1)
        val item2 = createItem("m2", BigDecimal("5.0"), ingredientId = ing1)
        
        val itemsFlow = MutableStateFlow(listOf(item1, item2))
        every { activityRepository.observeActivity(any()) } returns itemsFlow
        
        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.quantitySummary).isNotNull()
        assertThat(state.summary.quantitySummary!!.netQuantity).isEqualTo(BigDecimal("15.0"))

        // Add second ingredient
        itemsFlow.value = listOf(item1, item2, createItem("m3", BigDecimal("1.0"), ingredientId = IngredientId("ing2")))
        advanceUntilIdle()
        
        val state2 = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state2.summary.quantitySummary).isNull()
    }

    @Test
    fun `reset clears filters and search`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onFilterChange(InventoryActivityFilters(direction = InventoryActivityDirection.IN))
        viewModel.onSearchQueryChange("test")
        advanceUntilIdle()
        
        viewModel.resetFilters()
        advanceUntilIdle()
        
        assertThat(viewModel.filters.value).isEqualTo(InventoryActivityFilters())
        assertThat(viewModel.searchQuery.value).isEmpty()
    }

    @Test
    fun `retry resubscribes after activity repository error`() = runTest {
        val item = createItem(
            id = "movement-1",
            qty = BigDecimal.ONE
        )

        every {
            activityRepository.observeActivity(any())
        } returnsMany listOf(
            kotlinx.coroutines.flow.flow { throw IllegalStateException("Initial failure") },
            flowOf(listOf(item))
        )

        val viewModel = createViewModel()

        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isInstanceOf(InventoryActivityListScreenState.LoadError::class.java)

        viewModel.onRetry()
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.items).containsExactly(item)

        verify(atLeast = 2) {
            activityRepository.observeActivity(any())
        }
    }

    @Test
    fun `repeated retry failures produce load error`() = runTest {
        every {
            activityRepository.observeActivity(any())
        } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("Failure") }

        val viewModel = createViewModel()

        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(InventoryActivityListScreenState.LoadError::class.java)

        viewModel.onRetry()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(InventoryActivityListScreenState.LoadError::class.java)
    }

    @Test
    fun `missing saved categories defaults to all categories`() {
        val viewModel = createViewModel(SavedStateHandle())

        assertThat(viewModel.filters.value.categories)
            .containsExactlyElementsIn(InventoryActivityCategory.entries)
    }

    @Test
    fun `restores explicitly empty category selection`() {
        val handle = SavedStateHandle(
            mapOf("categories" to emptyList<String>())
        )

        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories).isEmpty()
    }

    @Test
    fun `restores saved category selection`() {
        val handle = SavedStateHandle(
            mapOf(
                "categories" to listOf(
                    InventoryActivityCategory.PURCHASE.name,
                    InventoryActivityCategory.WASTE.name
                )
            )
        )

        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories)
            .containsExactly(
                InventoryActivityCategory.PURCHASE,
                InventoryActivityCategory.WASTE
            )
    }

    @Test
    fun `defensively parses malformed categories`() {
        val handle = SavedStateHandle(mapOf("categories" to listOf("INVALID", "PURCHASE")))
        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories).containsExactly(InventoryActivityCategory.PURCHASE)
    }

    @Test
    fun `malformed categories restore empty set if no valid values`() {
        val handle = SavedStateHandle(mapOf("categories" to listOf("INVALID")))
        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories).isEmpty()
    }

    @Test
    fun `search matches localized text`() = runTest {
        val item = createItem("m1", BigDecimal("10.0")).copy(
            sourceInfo = InventoryActivitySourceInfo.Waste(WasteReason.SPOILED, "Storage", true)
        )
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(item))
        
        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // "Spoiled" in our simple resolver
        viewModel.onSearchQueryChange("Spoiled")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.items).hasSize(1)

        viewModel.onSearchQueryChange("Unknown")
        advanceUntilIdle()
        
        val stateEmpty = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(stateEmpty.items).isEmpty()
    }

    @Test
    fun `restores valid Custom range`() = runTest {
        val start = java.time.LocalDate.of(2026, 8, 1)
        val end = java.time.LocalDate.of(2026, 8, 4)
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to start.toString(),
            "customEndDate" to end.toString()
        ))
        val viewModel = createViewModel(handle)
        
        val range = viewModel.filters.value.dateRange as InventoryActivityDateRange.Custom
        assertThat(range.startDate).isEqualTo(start)
        assertThat(range.endDateInclusive).isEqualTo(end)
    }

    @Test
    fun `falls back on malformed start date`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to "invalid",
            "customEndDate" to "2026-08-04"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.filters.value.dateRange).isEqualTo(InventoryActivityDateRange.Last30Days)
    }

    @Test
    fun `falls back on malformed end date`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to "2026-08-01",
            "customEndDate" to "invalid"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.filters.value.dateRange).isEqualTo(InventoryActivityDateRange.Last30Days)
    }

    @Test
    fun `falls back on missing start date`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customEndDate" to "2026-08-04"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.filters.value.dateRange).isEqualTo(InventoryActivityDateRange.Last30Days)
    }

    @Test
    fun `falls back on missing end date`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to "2026-08-01"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.filters.value.dateRange).isEqualTo(InventoryActivityDateRange.Last30Days)
    }

    @Test
    fun `falls back on reversed range`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to "2026-08-10",
            "customEndDate" to "2026-08-01"
        ))
        val viewModel = createViewModel(handle)
        assertThat(viewModel.filters.value.dateRange).isEqualTo(InventoryActivityDateRange.Last30Days)
    }

    @Test
    fun `restores single day range`() = runTest {
        val date = java.time.LocalDate.of(2026, 8, 1)
        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to date.toString(),
            "customEndDate" to date.toString()
        ))
        val viewModel = createViewModel(handle)
        
        val range = viewModel.filters.value.dateRange as InventoryActivityDateRange.Custom
        assertThat(range.startDate).isEqualTo(date)
        assertThat(range.endDateInclusive).isEqualTo(date)
    }

    @Test
    fun `restored future end date is capped by today in repository query`() = runTest {
        val today = java.time.LocalDate.now()
        val zoneId = java.time.ZoneId.systemDefault()
        val future = today.plusDays(10)
        
        val observedQueries = mutableListOf<InventoryActivityQuery>()
        every { activityRepository.observeActivity(capture(observedQueries)) } returns flowOf(emptyList())

        val handle = SavedStateHandle(mapOf(
            "dateRangeKind" to "Custom",
            "customStartDate" to today.minusDays(1).toString(),
            "customEndDate" to future.toString()
        ))
        val viewModel = createViewModel(handle)
        
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        
        val query = observedQueries.last()
        
        val expectedStart = today.minusDays(1).atStartOfDay(zoneId).toInstant()
        val expectedEndExclusive = today.plusDays(1).atStartOfDay(zoneId).toInstant()
        
        assertThat(query.startInclusive).isEqualTo(expectedStart)
        assertThat(query.endExclusive).isEqualTo(expectedEndExclusive)
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
        sourceInfo = InventoryActivitySourceInfo.Purchase("Supplier", "Inv", true),
        reversedByMovementId = null,
        reversalOfDisplay = null,
        reversedByDisplay = null
    )

    private class SimpleInventoryActivityTextResolver : InventoryActivityTextResolver {
        override fun categoryText(category: InventoryActivityCategory): String = category.name
        override fun sourceTitle(info: InventoryActivitySourceInfo): String = when (info) {
            is InventoryActivitySourceInfo.Purchase -> "Purchase from ${info.supplierName}"
            is InventoryActivitySourceInfo.Waste -> "Waste - ${info.reason}"
            is InventoryActivitySourceInfo.StockCount -> info.countName ?: "Count"
            is InventoryActivitySourceInfo.Production -> "Production - ${info.recipeName}"
            is InventoryActivitySourceInfo.Other -> "Other"
        }
        override fun sourceSubtitle(info: InventoryActivitySourceInfo): String? = null
        override fun wasteReasonText(reason: WasteReason): String = reason.name.lowercase().replaceFirstChar { it.uppercase() }
        override fun productionStatusText(status: DocumentStatus): String = status.name
    }
}
