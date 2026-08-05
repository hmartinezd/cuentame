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
    fun `missing saved categories defaults to all categories including UNKNOWN`() {
        val viewModel = createViewModel(SavedStateHandle())

        assertThat(viewModel.filters.value.categories)
            .containsExactlyElementsIn(InventoryActivityCategory.entries)
        assertThat(viewModel.filters.value.categories).contains(InventoryActivityCategory.UNKNOWN)
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
    fun `restores saved category selection including legitimate UNKNOWN`() {
        val handle = SavedStateHandle(
            mapOf(
                "categories" to listOf(
                    InventoryActivityCategory.PURCHASE.name,
                    InventoryActivityCategory.UNKNOWN.name
                )
            )
        )

        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories)
            .containsExactly(
                InventoryActivityCategory.PURCHASE,
                InventoryActivityCategory.UNKNOWN
            )
    }

    @Test
    fun `filter restoration ignores malformed names but preserves UNKNOWN`() {
        val handle = SavedStateHandle(
            mapOf("categories" to listOf("PURCHASE", "UNKNOWN", "invalid", ""))
        )
        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories)
            .containsExactly(InventoryActivityCategory.PURCHASE, InventoryActivityCategory.UNKNOWN)
    }

    @Test
    fun `restoration of only malformed names results in empty set`() {
        val handle = SavedStateHandle(mapOf("categories" to listOf("unknown", "INVALID")))
        val viewModel = createViewModel(handle)

        assertThat(viewModel.filters.value.categories).isEmpty()
    }

    @Test
    fun `persisting and restoring filters preserves UNKNOWN`() {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(handle)
        
        val filters = InventoryActivityFilters(
            categories = setOf(InventoryActivityCategory.UNKNOWN, InventoryActivityCategory.PURCHASE)
        )
        viewModel.onFilterChange(filters)
        
        // Create new VM from the same handle (which was updated by persistFilters)
        val secondViewModel = createViewModel(handle)
        assertThat(secondViewModel.filters.value.categories).isEqualTo(filters.categories)
    }

    @Test
    fun `unknown movement direction filtering`() = runTest {
        val unknownPos = createItem("m1", BigDecimal("10.0")).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        val unknownNeg = createItem("m2", BigDecimal("-5.0")).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        val unknownZero = createItem("m3", BigDecimal.ZERO).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        val items = listOf(unknownPos, unknownNeg, unknownZero)

        every { activityRepository.observeActivity(any()) } returns flowOf(items)

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // Visible under ALL
        assertThat((viewModel.uiState.value as InventoryActivityListScreenState.Ready).items).hasSize(3)

        // Excluded from IN
        viewModel.onFilterChange(InventoryActivityFilters(direction = InventoryActivityDirection.IN))
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as InventoryActivityListScreenState.Ready).items).isEmpty()

        // Excluded from OUT
        viewModel.onFilterChange(InventoryActivityFilters(direction = InventoryActivityDirection.OUT))
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as InventoryActivityListScreenState.Ready).items).isEmpty()
    }

    @Test
    fun `recognized movement direction filtering preserved`() = runTest {
        val pos = createItem("m1", BigDecimal("10.0")) // PURCHASE (IN)
        val neg = createItem("m2", BigDecimal("-5.0")).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.WASTE)) // WASTE (OUT)
        }
        val zero = createItem("m3", BigDecimal.ZERO)
        val items = listOf(pos, neg, zero)

        every { activityRepository.observeActivity(any()) } returns flowOf(items)

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // IN
        viewModel.onFilterChange(InventoryActivityFilters(direction = InventoryActivityDirection.IN))
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as InventoryActivityListScreenState.Ready).items).containsExactly(pos)

        // OUT
        viewModel.onFilterChange(InventoryActivityFilters(direction = InventoryActivityDirection.OUT))
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as InventoryActivityListScreenState.Ready).items).containsExactly(neg)
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
    fun `summary handles mixed valid and unknown movements correctly`() = runTest {
        val valid = createItem("m1", BigDecimal("10.0"), BigDecimal("20.0")) // PURCHASE
        val unknown = createItem("m2", BigDecimal("-5.0"), BigDecimal("-10.0")).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        val items = listOf(valid, unknown)

        every { activityRepository.observeActivity(any()) } returns flowOf(items)

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.movementCount).isEqualTo(2)
        // Only valid movement is counted in directional totals
        assertThat(state.summary.incomingMovementCount).isEqualTo(1)
        assertThat(state.summary.outgoingMovementCount).isEqualTo(0)
        assertThat(state.summary.valueAdded).isEqualTo(BigDecimal("20.0"))
        assertThat(state.summary.valueRemoved).isEqualTo(BigDecimal.ZERO)
        
        // Coverage should be partial because one movement is unknown
        assertThat(state.summary.valueCoverage).isEqualTo(InventoryActivityValueCoverage.PARTIAL)
        assertThat(state.summary.quantityCoverage).isEqualTo(InventoryActivityValueCoverage.PARTIAL)
        
        // Quantity summary also partial
        assertThat(state.summary.quantitySummary).isNotNull()
        assertThat(state.summary.quantitySummary!!.quantityIn).isEqualTo(BigDecimal("10.0"))
        assertThat(state.summary.quantitySummary!!.quantityOut).isEqualTo(BigDecimal.ZERO)
        assertThat(state.summary.quantitySummary!!.netQuantity).isEqualTo(BigDecimal("10.0"))
    }

    @Test
    fun `summary unavailable for only unknown movements`() = runTest {
        val unknown = createItem("m1", BigDecimal("10.0")).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(unknown))

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.movementCount).isEqualTo(1)
        assertThat(state.summary.incomingMovementCount).isEqualTo(0)
        assertThat(state.summary.valueCoverage).isEqualTo(InventoryActivityValueCoverage.UNAVAILABLE)
        assertThat(state.summary.quantityCoverage).isEqualTo(InventoryActivityValueCoverage.UNAVAILABLE)
        assertThat(state.summary.quantitySummary).isNull()
    }

    @Test
    fun `quantity summary for mixed incompatible ingredients with unknown`() = runTest {
        val ing1 = IngredientId("ing1")
        val ing2 = IngredientId("ing2")
        val knownIng1 = createItem("m1", BigDecimal("10.0"), ingredientId = ing1)
        val unknownIng2 = createItem("m2", BigDecimal("5.0"), ingredientId = ing2).let {
            it.copy(movement = it.movement.copy(movementType = InventoryMovementType.UNKNOWN))
        }
        
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(knownIng1, unknownIng2))

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        // Summary should still be produced for ing1
        assertThat(state.summary.quantitySummary).isNotNull()
        assertThat(state.summary.quantitySummary!!.ingredientName).isEqualTo("Ing")
        assertThat(state.summary.quantityCoverage).isEqualTo(InventoryActivityValueCoverage.PARTIAL)
    }

    @Test
    fun `unknown source does not affect quantity summary if movement type is known`() = runTest {
        val item = createItem("m1", BigDecimal("10.0")).let {
            it.copy(movement = it.movement.copy(sourceDocumentType = SourceDocumentType.UNKNOWN))
        }
        every { activityRepository.observeActivity(any()) } returns flowOf(listOf(item))

        val viewModel = createViewModel()
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as InventoryActivityListScreenState.Ready
        assertThat(state.summary.quantityCoverage).isEqualTo(InventoryActivityValueCoverage.COMPLETE)
        assertThat(state.summary.quantitySummary!!.quantityIn).isEqualTo(BigDecimal("10.0"))
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
            is InventoryActivitySourceInfo.Other -> if (info.sourceDocumentType == SourceDocumentType.UNKNOWN) "Unknown Source" else "Other"
        }
        override fun sourceSubtitle(info: InventoryActivitySourceInfo): String? = null
        override fun wasteReasonText(reason: WasteReason): String = reason.name
        override fun productionStatusText(status: DocumentStatus): String = status.name
    }
}
