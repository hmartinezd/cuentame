package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.PurchaseDetailItem
import com.miara.cuentame.core.model.dashboard.PurchaseDetailReport
import com.miara.cuentame.feature.reports.ui.PurchaseDetailScreen
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PurchaseDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun purchaseReport_ready_displaysSummaryAndItems() {
        val now = Instant.now()
        val period = ReportingPeriod(now.minusSeconds(1000), now)
        val readyState = DetailReportScreenState.Ready(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            report = PurchaseDetailReport(
                rows = listOf(
                    PurchaseDetailItem(
                        purchaseId = PurchaseReceiptId("p1"),
                        purchaseDate = now,
                        postedAt = now,
                        supplierName = "Supplier A",
                        lineCount = 5,
                        total = BigDecimal("500.00")
                    )
                ),
                period = period,
                totalSpend = BigDecimal("500.00"),
                recordCount = 1
            )
        )

        composeTestRule.setContent {
            PurchaseDetailScreen(
                uiState = readyState,
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                onRangeSelected = {},
                onRetry = {}
            )
        }

        // Summary
        composeTestRule.onNodeWithTag("purchase_report_total")
            .assertTextContains("$500.00", substring = true)
        composeTestRule.onNodeWithTag("purchase_report_count")
            .assertTextContains("1")

        // Row
        val row = composeTestRule.onNodeWithTag("purchase_report_item_p1")
        row.assertIsDisplayed()
        row.assertTextContains("Supplier A", substring = true)
        row.assertTextContains("$500.00", substring = true)
        row.assertTextContains("5 items", substring = true)
        row.assertTextContains("Posted", substring = true)
    }

    @Test
    fun purchaseReport_rangeSelection_callsCallback() {
        var selectedRange: DashboardDateRange? = null
        val readyState = DetailReportScreenState.Ready(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            report = PurchaseDetailReport(emptyList(), ReportingPeriod(Instant.now(), Instant.now()), BigDecimal.ZERO, 0)
        )

        composeTestRule.setContent {
            PurchaseDetailScreen(
                uiState = readyState,
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                onRangeSelected = { selectedRange = it },
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithTag("purchase_report_range_7").performClick()
        com.google.common.truth.Truth.assertThat(selectedRange).isEqualTo(DashboardDateRange.LAST_7_DAYS)
    }
}
