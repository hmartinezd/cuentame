package com.miara.cuentame.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Straighten
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
import com.miara.cuentame.core.model.dashboard.DashboardActivityType
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.feature.reports.ui.RefreshErrorBanner
import com.miara.cuentame.feature.reports.ui.RefreshIndicator
import java.time.format.FormatStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeRoute(
    onLogWaste: () -> Unit,
    onViewWaste: () -> Unit,
    onNewPurchase: () -> Unit,
    onStartCount: () -> Unit,
    onViewReports: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    HomeScreen(
        uiState = uiState,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::onRetry,
        onLogWaste = onLogWaste,
        onViewWaste = onViewWaste,
        onNewPurchase = onNewPurchase,
        onStartCount = onStartCount,
        onViewReports = onViewReports,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeScreenState,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    onLogWaste: () -> Unit,
    onViewWaste: () -> Unit,
    onNewPurchase: () -> Unit,
    onStartCount: () -> Unit,
    onViewReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) {
        when (uiState) {
            is HomeScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().testTag("home_loading"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HomeScreenState.SetupRequired -> {
                Box(modifier = Modifier.fillMaxSize().testTag("home_setup_required"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.setup_required_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.setup_required_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            is HomeScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().testTag("home_error"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.dashboard_error_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.dashboard_error_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp).testTag("home_retry_button")) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                    }
                }
            }
            is HomeScreenState.Ready -> {
                DashboardContent(
                    state = uiState,
                    onRangeSelected = onRangeSelected,
                    onRetry = onRetry,
                    onLogWaste = onLogWaste,
                    onViewWaste = onViewWaste,
                    onNewPurchase = onNewPurchase,
                    onStartCount = onStartCount,
                    onViewReports = onViewReports
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: HomeScreenState.Ready,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    onLogWaste: () -> Unit,
    onViewWaste: () -> Unit,
    onNewPurchase: () -> Unit,
    onStartCount: () -> Unit,
    onViewReports: () -> Unit
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
            DashboardHeader(state.restaurantName)
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeSelector(selected = state.selectedRange, onSelected = onRangeSelected)

                if (state.selectedRange != state.loadedRange) {
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
                    
                    val contextMessage = if (state.refreshError) {
                        stringResource(R.string.refresh_context_error_dashboard, selectedLabel, loadedLabel)
                    } else {
                        stringResource(R.string.refresh_context_updating_dashboard, loadedLabel, selectedLabel)
                    }
                    
                    Text(
                        text = contextMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.refreshError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("home_range_context")
                    )
                }

                if (state.isRefreshing) {
                    RefreshIndicator(
                        testTag = "home_refreshing",
                        label = stringResource(R.string.updating_dashboard)
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
                        testTag = "home_refresh_error",
                        onRetry = onRetry,
                        message = stringResource(R.string.refresh_context_error_dashboard, selectedLabel, loadedLabel)
                    )
                }
            }
        }

        item {
            KpiSection(state, restaurantLocale)
        }

        item {
            QuickActionsSection(onLogWaste, onNewPurchase, onStartCount, onViewReports, onViewWaste)
        }

        item {
            DataCompletenessSection(state, restaurantLocale)
        }

        item {
            StockCountSummarySection(state, restaurantLocale)
        }

        item {
            TopWasteSection(state, restaurantLocale)
        }

        item {
            RecentActivitySection(state, restaurantLocale)
        }
    }
}

@Composable
private fun DashboardHeader(restaurantName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = restaurantName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("dashboard_restaurant_name")
        )
        Text(
            text = stringResource(R.string.dashboard_title),
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
            .testTag("home_date_range_selector")
            .semantics(mergeDescendants = true) {
                contentDescription = filterLabel
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardDateRange.entries.forEach { range ->
            // Precompute localized strings before using in semantics
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
                        DashboardDateRange.LAST_7_DAYS -> "home_range_7"
                        DashboardDateRange.LAST_30_DAYS -> "home_range_30"
                        DashboardDateRange.LAST_90_DAYS -> "home_range_90"
                    })
                    .semantics {
                        contentDescription = rangeDescription
                    }
            )
        }
    }
}

@Composable
private fun KpiSection(state: HomeScreenState.Ready, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KpiCard(
                title = stringResource(R.string.inventory_value_label),
                value = Formatters.formatCurrency(state.dashboard.inventoryValue, state.currencyCode, locale),
                modifier = Modifier.weight(1f).testTag("dashboard_inventory_value")
            )
            KpiCard(
                title = stringResource(R.string.negative_balances_label),
                value = state.dashboard.negativeBalanceCount.toString(),
                modifier = Modifier.weight(1f).testTag("dashboard_negative_balance_count"),
                valueColor = if (state.dashboard.negativeBalanceCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        
        KpiCard(
            title = stringResource(R.string.purchase_spend_label),
            value = Formatters.formatCurrency(state.dashboard.purchaseSpend.value, state.currencyCode, locale),
            comparison = state.dashboard.purchaseSpend,
            locale = locale,
            modifier = Modifier.fillMaxWidth().testTag("dashboard_purchase_spend")
        )

        KpiCard(
            title = stringResource(R.string.waste_value_label),
            value = Formatters.formatCurrency(state.dashboard.wasteValue.value, state.currencyCode, locale),
            comparison = state.dashboard.wasteValue,
            locale = locale,
            modifier = Modifier.fillMaxWidth().testTag("dashboard_waste_value")
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    comparison: DashboardMetricUiModel? = null,
    locale: Locale = Locale.getDefault(),
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val trendDescription = if (comparison != null) {
        val directionText = when (comparison.comparisonState) {
            MetricComparisonState.INCREASE -> {
                val percentText = Formatters.formatPercent(comparison.percentageChange!!, locale)
                stringResource(R.string.trend_increase, percentText)
            }
            MetricComparisonState.DECREASE -> {
                val percentText = Formatters.formatPercent(comparison.percentageChange!!, locale)
                stringResource(R.string.trend_decrease, percentText)
            }
            MetricComparisonState.NEW -> stringResource(R.string.comparison_new)
            MetricComparisonState.NO_CHANGE -> stringResource(R.string.trend_no_change)
            MetricComparisonState.UNAVAILABLE -> stringResource(R.string.not_applicable)
        }
        "$directionText ${stringResource(R.string.from_previous_period)}"
    } else ""

    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$title: $value. $trendDescription"
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
            
            comparison?.let {
                MetricTrend(it, locale)
            }
        }
    }
}

@Composable
private fun MetricTrend(comparison: DashboardMetricUiModel, locale: Locale) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (text, color, icon) = when (comparison.comparisonState) {
            MetricComparisonState.INCREASE -> {
                val percentText = Formatters.formatPercent(comparison.percentageChange!!, locale)
                Triple(
                    stringResource(R.string.trend_increase, percentText),
                    MaterialTheme.colorScheme.primary,
                    Icons.Default.ArrowUpward
                )
            }
            MetricComparisonState.DECREASE -> {
                val percentText = Formatters.formatPercent(comparison.percentageChange!!, locale)
                Triple(
                    stringResource(R.string.trend_decrease, percentText),
                    MaterialTheme.colorScheme.primary,
                    Icons.Default.ArrowDownward
                )
            }
            MetricComparisonState.NEW -> Triple(
                stringResource(R.string.comparison_new),
                MaterialTheme.colorScheme.secondary,
                Icons.Default.FiberNew
            )
            MetricComparisonState.NO_CHANGE -> Triple(
                stringResource(R.string.trend_no_change),
                MaterialTheme.colorScheme.secondary,
                Icons.Default.Remove
            )
            MetricComparisonState.UNAVAILABLE -> Triple(
                stringResource(R.string.not_applicable),
                MaterialTheme.colorScheme.outline,
                null
            )
        }

        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
        Text(
            text = " ${stringResource(R.string.from_previous_period)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun QuickActionsSection(
    onLogWaste: () -> Unit,
    onNewPurchase: () -> Unit,
    onStartCount: () -> Unit,
    onViewReports: () -> Unit,
    onViewWasteHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionButton(Icons.Default.Delete, stringResource(R.string.log_waste_action), onLogWaste, Modifier.weight(1f).testTag("log_waste_button"))
            QuickActionButton(Icons.Default.History, stringResource(R.string.waste_history), onViewWasteHistory, Modifier.weight(1f).testTag("view_waste_button"))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionButton(Icons.Default.ShoppingCart, stringResource(R.string.new_purchase_action), onNewPurchase, Modifier.weight(1f).testTag("new_purchase_button"))
            QuickActionButton(Icons.Default.Straighten, stringResource(R.string.start_count_action), onStartCount, Modifier.weight(1f).testTag("start_count_button"))
        }
        QuickActionButton(Icons.Default.BarChart, stringResource(R.string.view_reports_action), onViewReports, Modifier.fillMaxWidth().testTag("view_reports_button"))
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = label
        },
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DataCompletenessSection(state: HomeScreenState.Ready, locale: Locale) {
    Card(modifier = Modifier.fillMaxWidth().testTag("dashboard_data_completeness")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.data_completeness_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            val coverageRatio = stringResource(R.string.coverage_format, state.dashboard.valuedIngredientCount, state.dashboard.stockedIngredientCount)
            val coveragePercentage = if (state.dashboard.stockedIngredientCount == 0) {
                stringResource(R.string.not_applicable)
            } else {
                state.dashboard.costCoverage?.let { Formatters.formatPercent(it, locale) } ?: stringResource(R.string.not_applicable)
            }

            DataQualityRow(stringResource(R.string.valuation_coverage_label), "$coverageRatio ($coveragePercentage)")
            DataQualityRow(stringResource(R.string.missing_costs_label), state.dashboard.missingCostCount.toString())
            DataQualityRow(stringResource(R.string.missing_unit_options_label), state.dashboard.missingOptionsCount.toString())
        }
    }
}

@Composable
private fun DataQualityRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
private fun StockCountSummarySection(state: HomeScreenState.Ready, locale: Locale) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("dashboard_stock_count_summary")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.count_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            DataQualityRow(stringResource(R.string.completed_counts_label), state.dashboard.completedCountCount.toString())
            DataQualityRow(stringResource(R.string.adjusted_lines_label), state.dashboard.adjustedLineCount.toString())
            
            val recentText = state.dashboard.mostRecentCompletedCountAt?.let { dateFormatter.format(it) } ?: stringResource(R.string.not_applicable)
            DataQualityRow(stringResource(R.string.most_recent_count_label), recentText)
        }
    }
}

@Composable
private fun TopWasteSection(state: HomeScreenState.Ready, locale: Locale) {
    Column(modifier = Modifier.testTag("dashboard_top_waste_list")) {
        Text(stringResource(R.string.top_waste_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        
        if (state.dashboard.topWasteItems.isEmpty()) {
            Text(
                text = stringResource(R.string.no_posted_waste_period), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("dashboard_top_waste_empty")
            )
        } else {
            state.dashboard.topWasteItems.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(stringResource(R.string.items_count_format, item.eventCount)) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Formatters.formatCurrency(item.totalValue, state.currencyCode, locale), fontWeight = FontWeight.Bold)
                            Text(Formatters.formatQuantity(item.quantityBase, item.unitSymbol), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.testTag("dashboard_top_waste_${item.ingredientId.value}")
                )
            }
        }
    }
}

@Composable
private fun RecentActivitySection(state: HomeScreenState.Ready, locale: Locale) {
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }

    Column(modifier = Modifier.testTag("dashboard_recent_activity_list")) {
        Text(stringResource(R.string.recent_activity_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        
        if (state.dashboard.recentActivity.isEmpty()) {
            Text(
                text = stringResource(R.string.no_recent_activity), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("dashboard_recent_activity_empty")
            )
        } else {
            state.dashboard.recentActivity.forEach { item ->
                val typeLabel = stringResource(when(item.type) {
                    DashboardActivityType.PURCHASE -> R.string.activity_type_purchase
                    DashboardActivityType.WASTE -> R.string.activity_type_waste
                    DashboardActivityType.STOCK_COUNT -> R.string.activity_type_stock_count
                })
                val statusLabel = stringResource(when(item.status) {
                    "POSTED" -> R.string.activity_status_posted
                    "VOIDED" -> R.string.activity_status_voided
                    "COMPLETED" -> R.string.activity_status_completed
                    else -> R.string.not_applicable
                })
                val displayName = if (item.displayName.isNullOrBlank()) typeLabel else item.displayName
                val dateTimeText = dateTimeFormatter.format(item.timestamp)

                // Build semantic description using localized format
                val semanticDesc = if (item.value != null) {
                    val valueText = Formatters.formatCurrency(item.value, state.currencyCode, locale)
                    stringResource(R.string.activity_description_format, typeLabel, displayName, statusLabel, dateTimeText, valueText)
                } else {
                    stringResource(R.string.activity_description_no_value, typeLabel, displayName, statusLabel, dateTimeText)
                }

                ListItem(
                    modifier = Modifier
                        .testTag("dashboard_activity_${item.type.name}_${item.id}")
                        .semantics(mergeDescendants = true) {
                            contentDescription = semanticDesc
                        },
                    headlineContent = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall).padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(displayName)
                        }
                    },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dateTimeText)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("•")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusLabel)
                        }
                    },
                    trailingContent = item.value?.let {
                        { Text(Formatters.formatCurrency(it, state.currencyCode, locale), fontWeight = FontWeight.Bold) }
                    }
                )
            }
        }
    }
}
