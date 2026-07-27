package com.miara.cuentame.core.database.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.model.*
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class RoomDashboardRepositoryTest {

    private val inventoryProjectionDao = mockk<InventoryProjectionDao>()
    private val purchaseDao = mockk<PurchaseDao>()
    private val movementDao = mockk<InventoryMovementDao>()
    private val stockCountDao = mockk<StockCountDao>()
    private val ingredientDao = mockk<IngredientDao>()
    private val timeProvider = mockk<TimeProvider>()
    private lateinit var periodCalculator: ReportingPeriodCalculator
    private lateinit var repository: RoomDashboardRepository

    private val now = Instant.parse("2024-01-31T12:00:00Z")

    @Before
    fun setup() {
        every { timeProvider.now() } returns now
        periodCalculator = ReportingPeriodCalculator(timeProvider)
        repository = RoomDashboardRepository(
            inventoryProjectionDao,
            purchaseDao,
            movementDao,
            stockCountDao,
            ingredientDao,
            periodCalculator
        )

        // Default empty flows
        every { inventoryProjectionDao.observeValuationRows(any()) } returns flowOf(emptyList())
        every { purchaseDao.observeSpendRows(any(), any(), any()) } returns flowOf(emptyList())
        every { movementDao.observeWasteValueRows(any(), any(), any()) } returns flowOf(emptyList())
        every { stockCountDao.observeCompletedCountSummaries(any(), any(), any()) } returns flowOf(emptyList())
        every { stockCountDao.observeCompletedCountLines(any(), any(), any()) } returns flowOf(emptyList())
        every { ingredientDao.observeActiveIngredientsMissingOptionsCount(any()) } returns flowOf(0)
        every { movementDao.observeTopWasteRows(any(), any(), any()) } returns flowOf(emptyList())
        every { purchaseDao.observeRecentPurchaseActivity(any(), any()) } returns flowOf(emptyList())
        every { movementDao.observeRecentWasteActivity(any(), any()) } returns flowOf(emptyList())
        every { stockCountDao.observeRecentCountActivity(any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun `empty data returns zeroed snapshot`() = runTest {
        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.inventory.totalValue).isEqualTo(BigDecimal.ZERO)
            assertThat(snapshot.purchases.current).isEqualTo(BigDecimal.ZERO)
            assertThat(snapshot.waste.current).isEqualTo(BigDecimal.ZERO)
            assertThat(snapshot.completedCountCount).isEqualTo(0)
            assertThat(snapshot.topWasteItems).isEmpty()
            assertThat(snapshot.recentActivity).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calculate inventory valuation with multiple areas and negative quantities`() = runTest {
        val rows = listOf(
            InventoryValuationRow("ing-1", "Chicken", "lb", "10.0", "2.0", "area-1"),
            InventoryValuationRow("ing-1", "Chicken", "lb", "-2.0", "2.0", "area-2"), // Combined ing-1 = 8.0
            InventoryValuationRow("ing-2", "Beef", "lb", "5.0", "10.0", "area-1"),
            InventoryValuationRow("ing-3", "Milk", "gal", "1.0", null, "area-1") // Missing cost
        )
        every { inventoryProjectionDao.observeValuationRows("rest-1") } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            // (8.0 * 2.0) + (5.0 * 10.0) = 16.0 + 50.0 = 66.0
            assertThat(snapshot.inventory.totalValue.compareTo(BigDecimal("66.0"))).isEqualTo(0)
            assertThat(snapshot.inventory.stockedIngredientCount).isEqualTo(3) 
            assertThat(snapshot.inventory.valuedIngredientCount).isEqualTo(2) 
            assertThat(snapshot.inventory.missingCostCount).isEqualTo(1) 
            assertThat(snapshot.negativeBalanceCount).isEqualTo(1) 
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid inventory decimal throws error`() = runTest {
        val rows = listOf(InventoryValuationRow("ing-1", "Chicken", "lb", "bad", "2.0", "area-1"))
        every { inventoryProjectionDao.observeValuationRows("rest-1") } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            assertThat(awaitError()).isInstanceOf(ValidationError.InvalidDecimal::class.java)
        }
    }

    @Test
    fun `negative cost throws error`() = runTest {
        val rows = listOf(InventoryValuationRow("ing-1", "Chicken", "lb", "10.0", "-1.0", "area-1"))
        every { inventoryProjectionDao.observeValuationRows("rest-1") } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            assertThat(awaitError()).isInstanceOf(ValidationError.InvalidDecimal::class.java)
        }
    }

    @Test
    fun `calculate purchase spend comparison correctly`() = runTest {
        val periods = periodCalculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS)

        val currentRows = listOf(
            PurchaseSpendRow("p1", 1000L, 1000L, "Supplier A", "100.0"),
            PurchaseSpendRow("p2", 2000L, 2000L, "Supplier B", "50.50")
        )
        val previousRows = listOf(
            PurchaseSpendRow("p0", 500L, 500L, "Supplier C", "75.25")
        )

        every { purchaseDao.observeSpendRows("rest-1", periods.current.startInclusive.toEpochMilli(), periods.current.endExclusive.toEpochMilli()) } returns flowOf(currentRows)
        every { purchaseDao.observeSpendRows("rest-1", periods.previous.startInclusive.toEpochMilli(), periods.previous.endExclusive.toEpochMilli()) } returns flowOf(previousRows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.purchases.current.compareTo(BigDecimal("150.50"))).isEqualTo(0)
            assertThat(snapshot.purchases.previous.compareTo(BigDecimal("75.25"))).isEqualTo(0)
            assertThat(snapshot.purchases.absoluteChange.compareTo(BigDecimal("75.25"))).isEqualTo(0)
            assertThat(snapshot.purchases.percentageChange?.compareTo(BigDecimal("100.0"))).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `waste value uses historical snapshots`() = runTest {
        val periods = periodCalculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS)
        val rows = listOf(
            WasteValueRow("w1", "ing-1", "Chicken", "Area 1", "SPOILED", 1000L, "-5.0", "lb", "10.0", null),
            WasteValueRow("w2", "ing-2", "Milk", "Area 1", "EXPIRED", 2000L, "-2.0", "gal", "4.5", null)
        )
        
        every { movementDao.observeWasteValueRows("rest-1", periods.current.startInclusive.toEpochMilli(), periods.current.endExclusive.toEpochMilli()) } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.waste.current.compareTo(BigDecimal("14.5"))).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `null waste valuation throws error`() = runTest {
        val periods = periodCalculator.calculatePeriods(DashboardDateRange.LAST_30_DAYS)
        val rows = listOf(WasteValueRow("w1", "ing-1", "Chicken", "Area 1", "SPOILED", 1000L, "-5.0", "lb", null, null))
        
        every { movementDao.observeWasteValueRows("rest-1", periods.current.startInclusive.toEpochMilli(), periods.current.endExclusive.toEpochMilli()) } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            assertThat(awaitError()).isInstanceOf(ValidationError.MalformedInventoryMovementHistory::class.java)
        }
    }

    @Test
    fun `completed count summary is independent of lines`() = runTest {
        val summaries = listOf(
            CompletedCountSummaryRow("c1", 1000L),
            CompletedCountSummaryRow("c2", 2000L)
        )
        every { stockCountDao.observeCompletedCountSummaries(any(), any(), any()) } returns flowOf(summaries)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.completedCountCount).isEqualTo(2)
            assertThat(snapshot.mostRecentCompletedCountAt).isEqualTo(Instant.ofEpochMilli(2000L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent activity uses structured data without fallback prose`() = runTest {
        val p = RecentPurchaseActivityRow("p1", "POSTED", 3000L, "Supplier A", "100.0")

        every { purchaseDao.observeRecentPurchaseActivity("rest-1", 10) } returns flowOf(listOf(p))

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.recentActivity[0].displayName).isEqualTo("Supplier A")
            assertThat(snapshot.recentActivity[0].value!!.compareTo(BigDecimal("100.0"))).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
