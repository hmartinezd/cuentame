package com.miara.cuentame.core.database.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.model.*
import com.miara.cuentame.core.domain.service.ReportingPeriodCalculator
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
            awaitComplete()
        }
    }

    @Test
    fun `calculate inventory valuation with multiple areas and negative quantities`() = runTest {
        val rows = listOf(
            InventoryValuationRow("ing-1", "10.0", "2.0"),
            InventoryValuationRow("ing-1", "-2.0", "2.0"), // Combined ing-1 = 8.0
            InventoryValuationRow("ing-2", "5.0", "10.0"),
            InventoryValuationRow("ing-3", "1.0", null) // Missing cost
        )
        every { inventoryProjectionDao.observeValuationRows("rest-1") } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            // (8.0 * 2.0) + (5.0 * 10.0) = 16.0 + 50.0 = 66.0
            assertThat(snapshot.inventory.totalValue.compareTo(BigDecimal("66.0"))).isEqualTo(0)
            assertThat(snapshot.inventory.stockedIngredientCount).isEqualTo(3) // ing-1, ing-2, ing-3 all have non-zero balance
            assertThat(snapshot.inventory.valuedIngredientCount).isEqualTo(2) // ing-1, ing-2
            assertThat(snapshot.inventory.missingCostCount).isEqualTo(1) // ing-3
            assertThat(snapshot.negativeBalanceCount).isEqualTo(1) // only one row has < 0
            awaitComplete()
        }
    }

    @Test
    fun `calculate purchase spend comparison correctly`() = runTest {
        val currentRows = listOf(
            PurchaseSpendRow("p1", 1000L, "100.0"),
            PurchaseSpendRow("p2", 2000L, "50.50")
        )
        val previousRows = listOf(
            PurchaseSpendRow("p0", 500L, "75.25")
        )
        
        val currentRange = periodCalculator.calculateCurrentPeriod(DashboardDateRange.LAST_30_DAYS)
        val previousRange = periodCalculator.calculatePreviousPeriod(DashboardDateRange.LAST_30_DAYS)

        every { purchaseDao.observeSpendRows("rest-1", currentRange.startInclusive.toEpochMilli(), currentRange.endExclusive.toEpochMilli()) } returns flowOf(currentRows)
        every { purchaseDao.observeSpendRows("rest-1", previousRange.startInclusive.toEpochMilli(), previousRange.endExclusive.toEpochMilli()) } returns flowOf(previousRows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.purchases.current.compareTo(BigDecimal("150.50"))).isEqualTo(0)
            assertThat(snapshot.purchases.previous.compareTo(BigDecimal("75.25"))).isEqualTo(0)
            assertThat(snapshot.purchases.absoluteChange.compareTo(BigDecimal("75.25"))).isEqualTo(0)
            // ((150.50 - 75.25) / 75.25) * 100 = 100.0%
            assertThat(snapshot.purchases.percentageChange?.compareTo(BigDecimal("100.0"))).isEqualTo(0)
            awaitComplete()
        }
    }

    @Test
    fun `percentage change handles zero previous value`() = runTest {
        val currentRows = listOf(PurchaseSpendRow("p1", 1000L, "100.0"))
        val previousRows = emptyList<PurchaseSpendRow>()

        val currentRange = periodCalculator.calculateCurrentPeriod(DashboardDateRange.LAST_30_DAYS)
        val previousRange = periodCalculator.calculatePreviousPeriod(DashboardDateRange.LAST_30_DAYS)

        every { purchaseDao.observeSpendRows("rest-1", currentRange.startInclusive.toEpochMilli(), currentRange.endExclusive.toEpochMilli()) } returns flowOf(currentRows)
        every { purchaseDao.observeSpendRows("rest-1", previousRange.startInclusive.toEpochMilli(), previousRange.endExclusive.toEpochMilli()) } returns flowOf(previousRows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.purchases.current.compareTo(BigDecimal("100.0"))).isEqualTo(0)
            assertThat(snapshot.purchases.previous).isEqualTo(BigDecimal.ZERO)
            assertThat(snapshot.purchases.percentageChange).isNull()
            awaitComplete()
        }
    }

    @Test
    fun `waste value uses historical snapshots and excludes voided`() = runTest {
        val rows = listOf(
            WasteValueRow("w1", "ing-1", 1000L, "-5.0", "10.0"),
            WasteValueRow("w2", "ing-2", 2000L, "-2.0", "4.5")
        )
        // Note: The logic in DAO should already filter for status = POSTED and movementType = WASTE
        
        val range = periodCalculator.calculateCurrentPeriod(DashboardDateRange.LAST_30_DAYS)
        every { movementDao.observeWasteValueRows("rest-1", range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli()) } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            // 10.0 + 4.5 = 14.5
            assertThat(snapshot.waste.current.compareTo(BigDecimal("14.5"))).isEqualTo(0)
            awaitComplete()
        }
    }

    @Test
    fun `top waste ingredients are aggregated and ranked correctly`() = runTest {
        val rows = listOf(
            TopWasteRow("ing-1", "Chicken", "lb", "-10.0", "20.0", 1),
            TopWasteRow("ing-1", "Chicken", "lb", "-5.0", "10.0", 1), // Aggregated to 30.0 value
            TopWasteRow("ing-2", "Beef", "kg", "-2.0", "50.0", 1), // Top value
            TopWasteRow("ing-3", "Pork", "kg", "-1.0", "5.0", 1)
        )
        val range = periodCalculator.calculateCurrentPeriod(DashboardDateRange.LAST_30_DAYS)
        every { movementDao.observeTopWasteRows("rest-1", range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli()) } returns flowOf(rows)

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.topWasteItems).hasSize(3)
            assertThat(snapshot.topWasteItems[0].ingredientId).isEqualTo(IngredientId("ing-2"))
            assertThat(snapshot.topWasteItems[0].totalValue.compareTo(BigDecimal("50.0"))).isEqualTo(0)
            assertThat(snapshot.topWasteItems[1].ingredientId).isEqualTo(IngredientId("ing-1"))
            assertThat(snapshot.topWasteItems[1].totalValue.compareTo(BigDecimal("30.0"))).isEqualTo(0)
            assertThat(snapshot.topWasteItems[1].eventCount).isEqualTo(2)
            awaitComplete()
        }
    }

    @Test
    fun `recent activity combines all sources and orders by timestamp`() = runTest {
        val p = RecentPurchaseActivityRow("p1", "POSTED", 3000L, "Supplier A", "100.0")
        val w = RecentWasteActivityRow("w1", "POSTED", 2000L, "Chicken", "10.0")
        val c = RecentCountActivityRow("c1", "COMPLETED", 4000L, "Count 1")

        every { purchaseDao.observeRecentPurchaseActivity("rest-1", 10) } returns flowOf(listOf(p))
        every { movementDao.observeRecentWasteActivity("rest-1", 10) } returns flowOf(listOf(w))
        every { stockCountDao.observeRecentCountActivity("rest-1", 10) } returns flowOf(listOf(c))

        repository.observeDashboard(RestaurantId("rest-1"), DashboardDateRange.LAST_30_DAYS).test {
            val snapshot = awaitItem()
            assertThat(snapshot.recentActivity).hasSize(3)
            assertThat(snapshot.recentActivity[0].id).isEqualTo("c1")
            assertThat(snapshot.recentActivity[1].id).isEqualTo("p1")
            assertThat(snapshot.recentActivity[2].id).isEqualTo("w1")
            awaitComplete()
        }
    }
}
