package com.miara.cuentame.feature.reports.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.miara.cuentame.core.model.dashboard.WasteDetailItem
import com.miara.cuentame.core.model.dashboard.WasteDetailReport
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import com.miara.cuentame.feature.reports.viewmodel.WasteDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

import androidx.compose.foundation.lazy.rememberLazyListState
import com.miara.cuentame.core.presentation.ui.RefreshErrorBanner
import com.miara.cuentame.core.presentation.ui.RefreshIndicator
import com.miara.cuentame.core.presentation.ui.toLabelRes

@Composable
fun WasteDetailRoute(
    modifier: Modifier = Modifier,
    viewModel: WasteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WasteDetailScreen(
        uiState = uiState,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

@Composable
fun WasteDetailScreen(
    uiState: DetailReportScreenState<WasteDetailReport>,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("waste_report_screen")) {
        when (uiState) {
            is DetailReportScreenState.Loading -> DetailReportLoading("waste_report_loading")
            is DetailReportScreenState.SetupRequired -> DetailReportSetupRequired("waste_report_setup_required")
            is DetailReportScreenState.Error -> DetailReportError("waste_report_error", onRetry)
            is DetailReportScreenState.Ready -> {
                WasteDetailContent(
                    state = uiState,
                    onRangeSelected = onRangeSelected,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun WasteDetailContent(
    state: DetailReportScreenState.Ready<WasteDetailReport>,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit
) {
    val locale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }
    val scrollState = rememberLazyListState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("waste_report_list"),
        state = scrollState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailReportHeader(
                restaurantName = state.restaurantName,
                reportTitle = stringResource(R.string.waste_detail_title),
                testTag = "waste_report_header"
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeSelector(
                    selected = state.selectedRange ?: DashboardDateRange.LAST_30_DAYS,
                    onSelected = onRangeSelected,
                    testTag = "waste_report_range_selector"
                )

                if (state.selectedRange != null && state.loadedRange != null && state.selectedRange != state.loadedRange && !state.refreshError) {
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
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("waste_report_range_context")
                    )
                }

                if (state.isRefreshing) {
                    RefreshIndicator(testTag = "waste_report_refreshing")
                }
                if (state.refreshError) {
                    val selectedLabel = stringResource(when(state.selectedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                        else -> R.string.not_applicable
                    })
                    val loadedLabel = stringResource(when(state.loadedRange) {
                        DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                        DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                        DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
                        else -> R.string.not_applicable
                    })
                    RefreshErrorBanner(
                        testTag = "waste_report_refresh_error",
                        onRetry = onRetry,
                        message = stringResource(R.string.refresh_context_error, selectedLabel, loadedLabel)
                    )
                }
            }
        }

        item {
            WasteSummaryCard(state.report, state.currencyCode, locale)
        }

        if (state.report.rows.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("waste_report_empty")) {
                    Text(
                        stringResource(R.string.reports_no_posted_waste),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(state.report.rows, key = { it.wasteEventId.value }) { item ->
                WasteDetailRow(item, state.currencyCode, locale)
            }
        }
    }
}

@Composable
private fun WasteSummaryCard(
    report: WasteDetailReport,
    currencyCode: String,
    locale: Locale
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("waste_report_summary")) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryMetric(
                label = stringResource(R.string.waste_value_label),
                value = Formatters.formatCurrency(report.totalWasteValue, currencyCode, locale),
                testTag = "waste_report_total"
            )
            SummaryMetric(
                label = stringResource(R.string.posted_waste_events_label),
                value = report.recordCount.toString(),
                testTag = "waste_report_count"
            )
        }
    }
}

@Composable
private fun WasteDetailRow(
    item: WasteDetailItem,
    currencyCode: String,
    locale: Locale
) {
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault())
    }
    val formattedDateTime = dateTimeFormatter.format(item.timestamp)
    val formattedQuantity = Formatters.formatQuantity(item.quantityBase, item.baseUnitSymbol)
    val formattedValue = Formatters.formatCurrency(item.historicalValue, currencyCode, locale)
    val reasonLabel = stringResource(item.reason.toLabelRes())
    
    val semanticsDesc = stringResource(
        R.string.waste_row_semantics,
        item.ingredientName,
        item.areaName,
        reasonLabel,
        formattedQuantity,
        formattedValue,
        formattedDateTime
    ) + (item.notes?.let { stringResource(R.string.waste_row_notes_semantics, it) } ?: "")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("waste_report_item_${item.wasteEventId.value}")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.ingredientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formattedValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.area_label) + ": " + item.areaName, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.reason_label) + ": " + reasonLabel, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(formattedQuantity, style = MaterialTheme.typography.bodySmall)
                    Text(formattedDateTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            if (!item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.notes_label) + ": " + item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun RangeSelector(
    selected: DashboardDateRange,
    onSelected: (DashboardDateRange) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardDateRange.entries.forEach { range ->
            val label = stringResource(when(range) {
                DashboardDateRange.LAST_7_DAYS -> R.string.range_7_days
                DashboardDateRange.LAST_30_DAYS -> R.string.range_30_days
                DashboardDateRange.LAST_90_DAYS -> R.string.range_90_days
            })
            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(label) },
                modifier = Modifier.testTag(when(range) {
                    DashboardDateRange.LAST_7_DAYS -> "waste_report_range_7"
                    DashboardDateRange.LAST_30_DAYS -> "waste_report_range_30"
                    DashboardDateRange.LAST_90_DAYS -> "waste_report_range_90"
                })
            )
        }
    }
}
