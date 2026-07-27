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
import com.miara.cuentame.core.model.dashboard.PurchaseDetailItem
import com.miara.cuentame.core.model.dashboard.PurchaseDetailReport
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import com.miara.cuentame.feature.reports.viewmodel.PurchaseDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Composable
fun PurchaseDetailRoute(
    modifier: Modifier = Modifier,
    viewModel: PurchaseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()

    PurchaseDetailScreen(
        uiState = uiState,
        selectedRange = selectedRange,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

@Composable
fun PurchaseDetailScreen(
    uiState: DetailReportScreenState<PurchaseDetailReport>,
    selectedRange: DashboardDateRange,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("purchase_report_screen")) {
        when (uiState) {
            is DetailReportScreenState.Loading -> DetailReportLoading("purchase_report_loading")
            is DetailReportScreenState.SetupRequired -> DetailReportSetupRequired("purchase_report_setup_required")
            is DetailReportScreenState.Error -> DetailReportError("purchase_report_error", onRetry)
            is DetailReportScreenState.Ready -> {
                PurchaseDetailContent(
                    state = uiState,
                    selectedRange = selectedRange,
                    onRangeSelected = onRangeSelected
                )
            }
        }
    }
}

@Composable
private fun PurchaseDetailContent(
    state: DetailReportScreenState.Ready<PurchaseDetailReport>,
    selectedRange: DashboardDateRange,
    onRangeSelected: (DashboardDateRange) -> Unit
) {
    val locale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("purchase_report_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailReportHeader(
                restaurantName = state.restaurantName,
                reportTitle = stringResource(R.string.purchase_detail_title),
                testTag = "purchase_report_header"
            )
        }

        item {
            RangeSelector(
                selected = selectedRange,
                onSelected = onRangeSelected,
                testTag = "purchase_report_range_selector"
            )
        }

        item {
            PurchaseSummaryCard(state.report, state.currencyCode, locale)
        }

        if (state.report.rows.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("purchase_report_empty")) {
                    Text(
                        stringResource(R.string.no_posted_purchases_period),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(state.report.rows, key = { it.purchaseId.value }) { item ->
                PurchaseDetailRow(item, state.currencyCode, locale)
            }
        }
    }
}

@Composable
private fun PurchaseSummaryCard(
    report: PurchaseDetailReport,
    currencyCode: String,
    locale: Locale
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("purchase_report_summary")) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryMetric(
                label = stringResource(R.string.purchase_spend_label),
                value = Formatters.formatCurrency(report.totalSpend, currencyCode, locale),
                testTag = "purchase_report_total"
            )
            SummaryMetric(
                label = stringResource(R.string.posted_receipts_label),
                value = report.recordCount.toString(),
                testTag = "purchase_report_count"
            )
        }
    }
}

@Composable
private fun PurchaseDetailRow(
    item: PurchaseDetailItem,
    currencyCode: String,
    locale: Locale
) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }
    val formattedDate = dateFormatter.format(item.purchaseDate)
    val formattedTotal = Formatters.formatCurrency(item.total, currencyCode, locale)
    val supplierName = item.supplierName.takeIf { !it.isNullOrBlank() } ?: stringResource(R.string.activity_type_purchase)
    
    val semanticsDesc = "$supplierName. Date: $formattedDate. Lines: ${item.lineCount}. Total: $formattedTotal. Status: ${stringResource(R.string.activity_status_posted)}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("purchase_report_item_${item.purchaseId.value}")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(supplierName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formattedTotal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.items_count_format, item.lineCount), style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                stringResource(R.string.activity_status_posted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
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
                    DashboardDateRange.LAST_7_DAYS -> "purchase_report_range_7"
                    DashboardDateRange.LAST_30_DAYS -> "purchase_report_range_30"
                    DashboardDateRange.LAST_90_DAYS -> "purchase_report_range_90"
                })
            )
        }
    }
}
