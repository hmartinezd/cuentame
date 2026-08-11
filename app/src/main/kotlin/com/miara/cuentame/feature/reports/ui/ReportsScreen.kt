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
import com.miara.cuentame.core.presentation.dashboard.DashboardMetricUiModel
import com.miara.cuentame.core.presentation.dashboard.MetricComparisonState
import com.miara.cuentame.feature.reports.viewmodel.ReportsScreenState
import com.miara.cuentame.feature.reports.viewmodel.ReportsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

import androidx.compose.foundation.lazy.rememberLazyListState
import com.miara.cuentame.core.presentation.ui.RefreshErrorBanner
import com.miara.cuentame.core.presentation.ui.RefreshIndicator

@Composable
fun ReportsRoute(
    onNavigateToInventory: () -> Unit,
    onNavigateToPurchases: (DashboardDateRange) -> Unit,
    onNavigateToWaste: (DashboardDateRange) -> Unit,
    onNavigateToPriceIncreases: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsScreen(
        uiState = uiState,
        onRangeSelected = viewModel::onRangeSelected,
        onNavigateToInventory = onNavigateToInventory,
        onNavigateToPurchases = onNavigateToPurchases,
        onNavigateToWaste = onNavigateToWaste,
        onNavigateToPriceIncreases = onNavigateToPriceIncreases,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

@Composable
fun ReportsScreen(
    uiState: ReportsScreenState,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToPurchases: (DashboardDateRange) -> Unit,
    onNavigateToWaste: (DashboardDateRange) -> Unit,
    onNavigateToPriceIncreases: () -> Unit = {},
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
                    onRangeSelected = onRangeSelected,
                    onRetry = onRetry,
                    onNavigateToInventory = onNavigateToInventory,
                    onNavigateToPurchases = onNavigateToPurchases,
                    onNavigateToWaste = onNavigateToWaste
                    ,onNavigateToPriceIncreases = onNavigateToPriceIncreases
                )
            }
        }
    }
}

@Composable
private fun ReportsContent(
    state: ReportsScreenState.Ready,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToPurchases: (DashboardDateRange) -> Unit,
    onNavigateToWaste: (DashboardDateRange) -> Unit
    ,onNavigateToPriceIncreases: () -> Unit = {}
) {
    val restaurantLocale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }
    val scrollState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ReportsHeader(state.restaurantName, state.loadedRange)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeSelector(selected = state.selectedRange, onSelected = onRangeSelected)

                if (state.selectedRange != state.loadedRange && !state.refreshError) {
                    val loadedLabel = stringResource(when(state.loadedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                    })
                    val selectedLabel = stringResource(when(state.selectedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                    })
                    
                    Text(
                        text = stringResource(R.string.refresh_context_updating, loadedLabel, selectedLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("reports_range_context")
                    )
                }

                if (state.isRefreshing) {
                    RefreshIndicator(
                        testTag = "reports_refreshing",
                        label = stringResource(R.string.updating_report)
                    )
                }
                if (state.refreshError) {
                    val selectedLabel = stringResource(when(state.selectedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                    })
                    val loadedLabel = stringResource(when(state.loadedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                    })
                    RefreshErrorBanner(
                        testTag = "reports_refresh_error",
                        onRetry = onRetry,
                        message = stringResource(R.string.refresh_context_error, selectedLabel, loadedLabel)
                    )
                }
            }
        }

        item {
            InventorySection(
                inventory = state.report.inventory,
                currencyCode = state.currencyCode,
                locale = restaurantLocale,
                onViewDetails = onNavigateToInventory
            )
        }

        item {
            ComparisonSection(
                title = stringResource(R.string.reports_purchasing),
                metric = state.report.purchases,
                currencyCode = state.currencyCode,
                locale = restaurantLocale,
                testTag = "reports_purchase_section",
                onViewDetails = { onNavigateToPurchases(state.selectedRange) },
                viewDetailsTag = "reports_view_purchase_details"
            )
        }

        item {
            ComparisonSection(
                title = stringResource(R.string.reports_waste),
                metric = state.report.waste,
                currencyCode = state.currencyCode,
                locale = restaurantLocale,
                testTag = "reports_waste_section",
                onViewDetails = { onNavigateToWaste(state.selectedRange) },
                viewDetailsTag = "reports_view_waste_details"
            )
        }

        item {
            OutlinedButton(onClick = onNavigateToPriceIncreases, modifier = Modifier.fillMaxWidth().testTag("reports_price_increases")) {
                Icon(Icons.Default.TrendingUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.price_increases_title))
            }
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
    val rangeText = stringResource(when(range) {
        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days_label
        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days_label
        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days_label
    })
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
        Text(
            text = rangeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
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
    locale: Locale,
    onViewDetails: () -> Unit
) {
    val sectionTitle = stringResource(R.string.reports_inventory_overview)
    val formattedValue = Formatters.formatCurrency(inventory.totalValue, currencyCode, locale)
    val coveragePercentage = if (inventory.stockedIngredientCount == 0) {
        stringResource(R.string.not_applicable)
    } else {
        inventory.costCoverage?.let { Formatters.formatPercent(it, locale) } ?: stringResource(R.string.not_applicable)
    }
    val coverageRatio = stringResource(R.string.coverage_format, inventory.valuedIngredientCount, inventory.stockedIngredientCount)
    
    val semanticsDesc = stringResource(
        R.string.reports_inventory_semantics,
        sectionTitle,
        formattedValue,
        coverageRatio,
        coveragePercentage,
        inventory.missingCostCount
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports_inventory_section")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sectionTitle, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewDetails, modifier = Modifier.testTag("reports_view_inventory_details")) {
                    Text(stringResource(R.string.reports_view_inventory_details))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            MetricRow(stringResource(R.string.inventory_value_label), formattedValue, "reports_inventory_value")
            MetricRow(stringResource(R.string.valuation_coverage_label), "$coverageRatio ($coveragePercentage)", "reports_inventory_coverage")
            MetricRow(stringResource(R.string.missing_costs_label), inventory.missingCostCount.toString(), "reports_inventory_missing_costs")
        }
    }
}

@Composable
private fun ComparisonSection(
    title: String,
    metric: DashboardMetricUiModel,
    currencyCode: String,
    locale: Locale,
    testTag: String,
    onViewDetails: () -> Unit,
    viewDetailsTag: String
) {
    val currentFormatted = Formatters.formatCurrency(metric.value, currencyCode, locale)
    val previousFormatted = metric.previousValue?.let { Formatters.formatCurrency(it, currencyCode, locale) } ?: stringResource(R.string.not_applicable)
    val absoluteFormatted = metric.absoluteChange?.let { Formatters.formatCurrency(it, currencyCode, locale) } ?: stringResource(R.string.not_applicable)
    
    val trendInfo = getTrendInfo(metric, locale)
    val previousContext = stringResource(R.string.from_previous_period)
    
    val semanticsDesc = stringResource(
        R.string.reports_comparison_semantics,
        title,
        currentFormatted,
        previousFormatted,
        absoluteFormatted,
        trendInfo.text,
        previousContext
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewDetails, modifier = Modifier.testTag(viewDetailsTag)) {
                    Text(stringResource(if (title == stringResource(R.string.reports_purchasing)) R.string.reports_view_purchase_details else R.string.reports_view_waste_details))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            MetricRow(stringResource(R.string.reports_current_period), currentFormatted, "${testTag}_current")
            metric.previousValue?.let {
                MetricRow(stringResource(R.string.reports_previous_period), previousFormatted, "${testTag}_previous")
            }
            metric.absoluteChange?.let {
                MetricRow(stringResource(R.string.reports_absolute_change), absoluteFormatted, "${testTag}_absolute")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            TrendLabel(trendInfo, "${testTag}_trend")
        }
    }
}

@Composable
private fun AlertsSection(alerts: ReportsAlertsUiModel) {
    val semanticsDesc = stringResource(
        R.string.reports_alerts_semantics,
        alerts.negativeBalanceCount,
        alerts.missingCostCount,
        alerts.missingOptionsCount
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports_alerts_section")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reports_operational_alerts), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            AlertRow(stringResource(R.string.negative_balances_label), alerts.negativeBalanceCount, isError = true, "reports_negative_balances")
            AlertRow(stringResource(R.string.missing_costs_label), alerts.missingCostCount, isError = false, "reports_missing_costs")
            AlertRow(stringResource(R.string.missing_unit_options_label), alerts.missingOptionsCount, isError = false, "reports_missing_unit_options")
        }
    }
}

@Composable
private fun StockCountSection(counts: ReportsCountUiModel, locale: Locale) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }

    val latestDate = counts.mostRecentCompletedCountAt?.let { dateFormatter.format(it) } ?: stringResource(R.string.not_applicable)
    
    val semanticsDesc = stringResource(
        R.string.reports_counts_semantics,
        counts.completedCountCount,
        counts.adjustedLineCount,
        latestDate
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports_stock_count_section")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reports_stock_count_summary), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            MetricRow(stringResource(R.string.completed_counts_label), counts.completedCountCount.toString(), "reports_completed_counts")
            MetricRow(stringResource(R.string.adjusted_lines_label), counts.adjustedLineCount.toString(), "reports_adjusted_lines")
            MetricRow(stringResource(R.string.most_recent_count_label), latestDate, "reports_most_recent_count")
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
                text = stringResource(R.string.reports_no_posted_waste), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("reports_top_waste_empty")
            )
        } else {
            items.forEach { item ->
                val formattedValue = Formatters.formatCurrency(item.totalValue, currencyCode, locale)
                val formattedQuantity = Formatters.formatQuantity(item.quantityBase, item.unitSymbol)
                
                val semanticsDesc = stringResource(
                    R.string.reports_top_waste_row_semantics,
                    item.name,
                    formattedValue,
                    formattedQuantity,
                    item.eventCount
                )

                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(stringResource(R.string.items_count_format, item.eventCount)) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formattedValue, fontWeight = FontWeight.Bold)
                            Text(formattedQuantity, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier
                        .testTag("reports_top_waste_${item.ingredientId.value}")
                        .semantics(mergeDescendants = true) {
                            contentDescription = semanticsDesc
                        }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, testTag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: $value"
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlertRow(label: String, count: Int, isError: Boolean, testTag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
private fun TrendLabel(info: TrendInfo, testTag: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    ) {
        info.icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = info.color)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = info.text, style = MaterialTheme.typography.labelMedium, color = info.color)
        Text(
            text = " ${stringResource(R.string.from_previous_period)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun getTrendInfo(metric: DashboardMetricUiModel, locale: Locale): TrendInfo {
    return when (metric.comparisonState) {
        MetricComparisonState.INCREASE -> {
            val percentText = Formatters.formatPercent(metric.percentageChange!!, locale)
            TrendInfo(stringResource(R.string.trend_increase, percentText), MaterialTheme.colorScheme.primary, Icons.Default.ArrowUpward)
        }
        MetricComparisonState.DECREASE -> {
            val percentText = Formatters.formatPercent(metric.percentageChange!!, locale)
            TrendInfo(stringResource(R.string.trend_decrease, percentText), MaterialTheme.colorScheme.primary, Icons.Default.ArrowDownward)
        }
        MetricComparisonState.NEW -> TrendInfo(stringResource(R.string.comparison_new), MaterialTheme.colorScheme.secondary, Icons.Default.FiberNew)
        MetricComparisonState.NO_CHANGE -> TrendInfo(stringResource(R.string.trend_no_change), MaterialTheme.colorScheme.outline, Icons.Default.Remove)
        MetricComparisonState.UNAVAILABLE -> TrendInfo(stringResource(R.string.not_applicable), MaterialTheme.colorScheme.outline, null)
    }
}

data class TrendInfo(
    val text: String,
    val color: Color,
    val icon: ImageVector?
)
