package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.WasteDetailItem
import com.miara.cuentame.core.model.dashboard.WasteDetailReport
import com.miara.cuentame.feature.reports.ui.WasteDetailScreen
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WasteDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun wasteReport_ready_displaysSummaryAndItems() {
        val now = Instant.now()
        val period = ReportingPeriod(now.minusSeconds(1000), now)
        val readyState = DetailReportScreenState.Ready(
            restaurantName = "Test Rest",
            currencyCode = "USD",
            localeTag = "en-US",
            report = WasteDetailReport(
                rows = listOf(
                    WasteDetailItem(
                        wasteEventId = WasteEventId("w1"),
                        ingredientId = IngredientId("ing1"),
                        ingredientName = "Chicken",
                        areaName = "Freezer",
                        reason = "SPOILED",
                        timestamp = now,
                        quantityBase = BigDecimal("10.0"),
                        baseUnitSymbol = "lb",
                        historicalValue = BigDecimal("50.00"),
                        notes = "Smells bad"
                    )
                ),
                period = period,
                totalWasteValue = BigDecimal("50.00"),
                recordCount = 1
            )
        )

        composeTestRule.setContent {
            WasteDetailScreen(
                uiState = readyState,
                selectedRange = DashboardDateRange.LAST_30_DAYS,
                onRangeSelected = {},
                onRetry = {}
            )
        }

        // Summary
        composeTestRule.onNodeWithTag("waste_report_total")
            .assertTextContains("$50.00", substring = true)

        // Row
        val row = composeTestRule.onNodeWithTag("waste_report_item_w1")
        row.assertIsDisplayed()
        row.assertTextContains("Chicken", substring = true)
        row.assertTextContains("$50.00", substring = true)
        row.assertTextContains("Freezer", substring = true)
        row.assertTextContains("SPOILED", substring = true)
        row.assertTextContains("10 lb", substring = true)
        row.assertTextContains("Smells bad", substring = true)
    }
}
