package com.miara.cuentame.feature.reports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.feature.home.DashboardMetricUiModel
import com.miara.cuentame.feature.home.MetricComparisonState
import com.miara.cuentame.feature.reports.viewmodel.ReportsScreenState
import com.miara.cuentame.feature.reports.viewmodel.ReportsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Composable
fun ReportsRoute(
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsScreen(
        uiState = uiState,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

@Composable
fun ReportsScreen(
    uiState: ReportsScreenState,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen")
    ) {
        when (uiState) {
            is ReportsScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().testTag("reports_loading"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ReportsScreenState.SetupRequired -> {
                Box(modifier = Modifier.fillMaxSize().testTag("reports_setup_required"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.setup_required_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.setup_required_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            is ReportsScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().testTag("reports_error"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reports_error_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.reports_error_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp).testTag("reports_retry_button")) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                    }
                }
            }
            is ReportsScreenState.Ready -> {
                ReportsContent(
                    state = uiState,
                    onRangeSelected = onRangeSelected
                )
            }
        }
    }
}

@Composable
private fun ReportsContent(
    state: ReportsScreenState.Ready,
    onRangeSelected: (DashboardDateRange) -> Unit
) {
    val restaurantLocale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ReportsHeader(state.restaurantName, state.selectedRange)
        }

        item {
            RangeSelector(selected = state.selectedRange, onSelected = onRangeSelected)
        }

        item {
            InventorySection(state.report.inventory, state.currencyCode, restaurantLocale)
        }

        item {
            ComparisonSection(
                title = stringResource(R.string.reports_purchasing),
                metric = state.report.purchases,
                currencyCode = state.currencyCode,
                locale = restaurantLocale,
                testTag = "reports_purchase_section"
            )
        }

        item {
            ComparisonSection(
                title = stringResource(R.string.reports_waste),
                metric = state.report.waste,
                currencyCode = state.currencyCode,
                locale = restaurantLocale,
                testTag = "reports_waste_section"
            )
        }

        item {
            AlertsSection(state.report.alerts)
        }

        item {
            StockCountSection(state.report.counts, restaurantLocale)
        }

        item {
            TopWasteSection(state.report.topWasteItems, state.currencyCode, restaurantLocale)
        }
    }
}

@Composable
private fun ReportsHeader(restaurantName: String, range: DashboardDateRange) {
    Column(modifier = Modifier.fillMaxWidth().testTag("reports_header")) {
        Text(
            text = restaurantName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.reports_overview_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun RangeSelector(
    selected: DashboardDateRange,
    onSelected: (DashboardDateRange) -> Unit
) {
    val filterLabel = stringResource(R.string.dashboard_date_range_filter_label)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports_date_range_selector")
            .semantics(mergeDescendants = true) {
                contentDescription = filterLabel
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardDateRange.entries.forEach { range ->
            val rangeLabel = stringResource(when(range) {
                DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
            })
            val rangeDescription = if (selected == range) {
                stringResource(R.string.dashboard_range_selected_description, rangeLabel)
            } else {
                stringResource(R.string.dashboard_range_description, rangeLabel)
            }

            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(rangeLabel) },
                modifier = Modifier
                    .testTag(when(range) {
                        DashboardDateRange.LAST_7_DAYS -> "reports_range_7"
                        DashboardDateRange.LAST_30_DAYS -> "reports_range_30"
                        DashboardDateRange.LAST_90_DAYS -> "reports_range_90"
                    })
                    .semantics {
                        contentDescription = rangeDescription
                    }
            )
        }
    }
}

@Composable
private fun InventorySection(
    inventory: ReportsInventoryUiModel,
    currencyCode: String,
    locale: Locale
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("reports_inventory_section")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reports_inventory_overview), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            val formattedValue = Formatters.formatCurrency(inventory.totalValue, currencyCode, locale)
            MetricRow(stringResource(R.string.inventory_value_label), formattedValue)
            
            val coverageRatio = stringResource(R.string.coverage_format, inventory.valuedIngredientCount, inventory.stockedIngredientCount)
            val coveragePercentage = if (inventory.stockedIngredientCount == 0) {
                stringResource(R.string.not_applicable)
            } else {
                inventory.costCoverage?.let { Formatters.formatPercent(it, locale) } ?: stringResource(R.string.not_applicable)
            }
            MetricRow(stringResource(R.string.valuation_coverage_label), "$coverageRatio ($coveragePercentage)")
            MetricRow(stringResource(R.string.missing_costs_label), inventory.missingCostCount.toString())
        }
    }
}

@Composable
private fun ComparisonSection(
    title: String,
    metric: DashboardMetricUiModel,
    currencyCode: String,
    locale: Locale,
    testTag: String
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            MetricRow(stringResource(R.string.reports_current_period), Formatters.formatCurrency(metric.value, currencyCode, locale))
            metric.previousValue?.let {
                MetricRow(stringResource(R.string.reports_previous_period), Formatters.formatCurrency(it, currencyCode, locale))
            }
            metric.absoluteChange?.let {
                MetricRow(stringResource(R.string.reports_absolute_change), Formatters.formatCurrency(it, currencyCode, locale))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            TrendLabel(metric, locale)
        }
    }
}

@Composable
private fun AlertsSection(alerts: ReportsAlertsUiModel) {
    Card(modifier = Modifier.fillMaxWidth().testTag("reports_alerts_section")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reports_operational_alerts), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            AlertRow(stringResource(R.string.negative_balances_label), alerts.negativeBalanceCount, isError = true)
            AlertRow(stringResource(R.string.missing_costs_label), alerts.missingCostCount, isError = false)
            AlertRow(stringResource(R.string.missing_unit_options_label), alerts.missingOptionsCount, isError = false)
        }
    }
}

@Composable
private fun StockCountSection(counts: ReportsCountUiModel, locale: Locale) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("reports_stock_count_section")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reports_stock_count_summary), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            MetricRow(stringResource(R.string.completed_counts_label), counts.completedCountCount.toString())
            MetricRow(stringResource(R.string.adjusted_lines_label), counts.adjustedLineCount.toString())
            
            val recentText = counts.mostRecentCompletedCountAt?.let { dateFormatter.format(it) } ?: stringResource(R.string.not_applicable)
            MetricRow(stringResource(R.string.most_recent_count_label), recentText)
        }
    }
}

@Composable
private fun TopWasteSection(
    items: List<com.miara.cuentame.core.model.dashboard.WasteReportItem>,
    currencyCode: String,
    locale: Locale
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("reports_top_waste_list")) {
        Text(stringResource(R.string.reports_top_waste), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.no_posted_waste_period), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            items.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(stringResource(R.string.items_count_format, item.eventCount)) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Formatters.formatCurrency(item.totalValue, currencyCode, locale), fontWeight = FontWeight.Bold)
                            Text(Formatters.formatQuantity(item.quantityBase, item.unitSymbol), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.testTag("reports_top_waste_${item.ingredientId.value}")
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlertRow(label: String, count: Int, isError: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val color = if (count > 0) {
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
        } else MaterialTheme.colorScheme.onSurfaceVariant
        
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun TrendLabel(metric: DashboardMetricUiModel, locale: Locale) {
    val (text, color, icon) = when (metric.comparisonState) {
        MetricComparisonState.INCREASE -> {
            val percentText = Formatters.formatPercent(metric.percentageChange!!, locale)
            Triple(stringResource(R.string.trend_increase, percentText), MaterialTheme.colorScheme.primary, Icons.Default.ArrowUpward)
        }
        MetricComparisonState.DECREASE -> {
            val percentText = Formatters.formatPercent(metric.percentageChange!!, locale)
            Triple(stringResource(R.string.trend_decrease, percentText), MaterialTheme.colorScheme.primary, Icons.Default.ArrowDownward)
        }
        MetricComparisonState.NEW -> Triple(stringResource(R.string.comparison_new), MaterialTheme.colorScheme.secondary, Icons.Default.FiberNew)
        MetricComparisonState.NO_CHANGE -> Triple(stringResource(R.string.trend_no_change), MaterialTheme.colorScheme.outline, Icons.Default.Remove)
        MetricComparisonState.UNAVAILABLE -> Triple(stringResource(R.string.not_applicable), MaterialTheme.colorScheme.outline, null)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            text = " ${stringResource(R.string.from_previous_period)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
