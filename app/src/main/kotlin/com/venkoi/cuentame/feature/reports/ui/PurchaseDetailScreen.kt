package com.venkoi.cuentame.feature.reports.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.util.ShareHelper
import com.venkoi.cuentame.core.designsystem.util.Formatters
import com.venkoi.cuentame.core.model.dashboard.DashboardDateRange
import com.venkoi.cuentame.core.model.dashboard.PurchaseDetailItem
import com.venkoi.cuentame.core.model.dashboard.PurchaseDetailReport
import com.venkoi.cuentame.feature.reports.viewmodel.DetailReportScreenState
import com.venkoi.cuentame.feature.reports.viewmodel.PurchaseDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

import com.venkoi.cuentame.core.presentation.ui.DetailReportError
import com.venkoi.cuentame.core.presentation.ui.DetailReportLoading
import com.venkoi.cuentame.core.presentation.ui.DetailReportSetupRequired
import com.venkoi.cuentame.core.presentation.ui.SummaryMetric
import androidx.compose.foundation.lazy.rememberLazyListState
import com.venkoi.cuentame.core.presentation.ui.RefreshErrorBanner
import com.venkoi.cuentame.core.presentation.ui.RefreshIndicator

@Composable
fun PurchaseDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportTitle = stringResource(R.string.export_purchases)
    val snackbarHostState = remember { SnackbarHostState() }
    val exportErrorMessage = stringResource(R.string.export_failed)

    LaunchedEffect(Unit) {
        viewModel.exportFlow.collect { csv ->
            ShareHelper.shareCsv(context, "purchase_export.csv", csv, exportTitle)
                .onFailure {
                    snackbarHostState.showSnackbar(exportErrorMessage)
                }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportError.collect {
            snackbarHostState.showSnackbar(exportErrorMessage)
        }
    }

    PurchaseDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::onRetry,
        onExport = viewModel::onExportRequested,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    uiState: DetailReportScreenState<PurchaseDetailReport>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("purchase_report_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState is DetailReportScreenState.Ready) {
                        IconButton(onClick = onExport, modifier = Modifier.testTag("purchase_export_button")) {
                            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.export_csv))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                is DetailReportScreenState.Loading -> DetailReportLoading("purchase_report_loading")
                is DetailReportScreenState.SetupRequired -> DetailReportSetupRequired("purchase_report_setup_required")
                is DetailReportScreenState.Error -> DetailReportError("purchase_report_error", onRetry)
                is DetailReportScreenState.Ready -> {
                    PurchaseDetailContent(
                        state = uiState,
                        onRangeSelected = onRangeSelected,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseDetailContent(
    state: DetailReportScreenState.Ready<PurchaseDetailReport>,
    onRangeSelected: (DashboardDateRange) -> Unit,
    onRetry: () -> Unit
) {
    val locale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }
    val scrollState = rememberLazyListState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("purchase_report_list"),
        state = scrollState,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeSelector(
                    selected = state.selectedRange ?: DashboardDateRange.LAST_30_DAYS,
                    onSelected = onRangeSelected,
                    testTag = "purchase_report_range_selector"
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
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("purchase_report_range_context")
                    )
                }

                if (state.isRefreshing) {
                    RefreshIndicator(testTag = "purchase_report_refreshing")
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
                        testTag = "purchase_report_refresh_error",
                        onRetry = onRetry,
                        message = stringResource(R.string.refresh_context_error, selectedLabel, loadedLabel)
                    )
                }
            }
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
    val statusLabel = stringResource(R.string.activity_status_posted)
    
    val semanticsDesc = stringResource(
        R.string.purchase_row_semantics,
        supplierName,
        formattedDate,
        item.lineCount,
        formattedTotal,
        statusLabel
    )

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
                statusLabel,
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
